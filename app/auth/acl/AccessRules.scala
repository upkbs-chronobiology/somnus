package auth.acl

import javax.inject.Inject
import javax.inject.Singleton

import scala.Function.tupled
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import auth.roles.Role
import models.AccessLevel
import models.AccessLevel.AccessLevel
import models.Answer
import models.AnswersRepository
import models.Questionnaire
import models.QuestionnairesRepository
import models.QuestionsRepository
import models.Schedule
import models.SchedulesRepository
import models.StudyAccessRepository
import models.StudyRepository
import models.User
import models.UserRepository
import util.Futures
import util.Futures.OptionFutureExtensions

@Singleton
class AccessRules @Inject() (
  userRepository: UserRepository,
  studyAccessRepo: StudyAccessRepository,
  studyRepo: StudyRepository,
  questionnairesRepo: QuestionnairesRepository,
  questionsRepo: QuestionsRepository,
  answersRepo: AnswersRepository,
  schedulesRepo: SchedulesRepository
)(implicit ec: ExecutionContext) {

  val DisconnectedEntitiesPublic = true

  def mayAccessUser(actor: User, targetUserId: Long, level: AccessLevel): Future[Boolean] = {
    actor match {
      case _ if actor.id == targetUserId => Future.successful(true)
      case _ if actor.hasRole(Role.Admin) => Future.successful(true)
      case _ =>
        // TODO: Implement editable access control (similar to studies)?
        // For now, all researchers have read access to all accounts
        val mayAccess = level <= AccessLevel.Read &&
          Role.level(actor) >= Role.level(Role.Researcher)
        Future.successful(mayAccess)
    }
  }

  def mayAccessStudy[B](user: User, studyId: Long, level: AccessLevel): Future[Boolean] = {
    user match {
      case _ if user.hasRole(Role.Admin) => Future.successful(true)
      case _ =>
        studyAccessRepo.read(user.id, studyId).map(_.exists(_.level >= level))
    }
  }

  def mayAccessQuestionnaire[B](user: User, questionnaireId: Long, level: AccessLevel): Future[Boolean] = {
    user match {
      case _ if user.hasRole(Role.Admin) => Future.successful(true)
      case _ =>
        questionnairesRepo
          .read(questionnaireId)
          .flatMapOption(q => mayAccessQuestionnaire(user, q, level))
          .map(_.getOrElse(false))
    }
  }

  def mayAccessQuestionnaire[B](user: User, questionnaire: Questionnaire, level: AccessLevel): Future[Boolean] = {
    Future
      .successful(questionnaire.studyId)
      .flatMapOption(
        studyId =>
          Futures.parallel(isStudyParticipant(user, studyId), mayAccessStudy(user, studyId, level)) map tupled(
            (p, a) => p && level <= AccessLevel.Read || a
          )
      )
      .map(_.getOrElse(DisconnectedEntitiesPublic))
  }

  private def isStudyParticipant(user: User, studyId: Long): Future[Boolean] =
    studyRepo.listParticipants(studyId).map(_.contains(user))

  def mayAccessQuestionnaire[B](userId: Long, questionnaireId: Long, level: AccessLevel): Future[Boolean] = {
    userRepository.get(userId) flatMap {
      case None => Future.successful(false)
      case Some(user) => mayAccessQuestionnaire(user, questionnaireId, level)
    }
  }

  def mayAccessQuestion[B](user: User, questionId: Long, level: AccessLevel): Future[Boolean] = {
    user match {
      case _ if user.hasRole(Role.Admin) => Future.successful(true)
      case _ =>
        questionsRepo
          .get(questionId)
          .mapOptionFlat(_.questionnaireId)
          .flatMapOption(mayAccessQuestionnaire(user, _, level))
          .map(_.getOrElse(DisconnectedEntitiesPublic))
    }
  }

  def mayAccessAnswer[B](user: User, answer: Answer, level: AccessLevel): Future[Boolean] = {
    user match {
      case _ if user.hasRole(Role.Admin) => Future.successful(true)
      case _ if user.id == answer.userId && level <= AccessLevel.Read => Future.successful(true)
      case _ => mayAccessQuestion(user, answer.questionId, level)
    }
  }

  def mayAccessAnswer[B](user: User, answerId: Long, level: AccessLevel): Future[Boolean] = {
    answersRepo
      .get(answerId)
      .flatMapOption(a => mayAccessAnswer(user, a, level))
      .map(_.getOrElse(false))
  }

  @SuppressWarnings(Array("org.wartremover.warts.Return"))
  def mayAccessSchedule(user: User, schedule: Schedule, level: AccessLevel): Future[Boolean] = {
    if (schedule.userId == user.id) return Future.successful(true) // scalastyle:ignore return
    mayAccessQuestionnaire(user, schedule.questionnaireId, level)
  }

  def mayAccessSchedule(user: User, scheduleId: Long, level: AccessLevel): Future[Boolean] = {
    schedulesRepo
      .get(scheduleId)
      .flatMapOption(s => mayAccessSchedule(user, s, level))
      .map(_.getOrElse(false))
  }
}
