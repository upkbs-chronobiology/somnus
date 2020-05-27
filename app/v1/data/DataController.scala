package v1.data

import javax.inject.Inject

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import auth.DefaultEnv
import auth.roles.ForEditors
import com.mohiva.play.silhouette.api.Silhouette
import models.Answer
import models.AnswersRepository
import models.Question
import models.Questionnaire
import models.QuestionnairesRepository
import models.QuestionsRepository
import models.Schedule
import models.SchedulesRepository
import models.Study
import models.StudyRepository
import models.User
import util.Export
import util.JsonError
import v1.RestBaseController
import v1.RestControllerComponents

class DataController @Inject() (
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  studiesRepo: StudyRepository,
  questionnairesRepo: QuestionnairesRepository,
  questionsRepo: QuestionsRepository,
  answersRepo: AnswersRepository,
  schedulesRepo: SchedulesRepository
)(implicit ec: ExecutionContext)
    extends RestBaseController(rcc) {

  def getCsvZipped(studyId: Long) = silhouette.SecuredAction(ForEditors).async {
    for {
      studyOption <- studiesRepo.read(studyId)
      questionnaires <- questionnairesRepo.listByStudy(studyId)
      questions <- Future.sequence(questionnaires.map(q => questionsRepo.listByQuestionnaire(q.id))).map(_.flatten)
      answers <- Future.sequence(questionnaires.map(q => answersRepo.listByQuestionnaire(q.id))).map(_.flatten)
      users <- studiesRepo.listParticipants(studyId)
      schedules <- Future.sequence(questionnaires.map(q => schedulesRepo.getByQuestionnaire(q.id))).map(_.flatten)
    } yield {
      studyOption match {
        case None => NotFound(JsonError(s"No study with id $studyId"))
        case Some(study) =>
          val studyCsv = Export.asCsv(paramNames(classOf[Study]), Seq(study))
          val questionnairesCsv = Export.asCsv(paramNames(classOf[Questionnaire]), questionnaires)
          val questionsCsv = Export.asCsv(paramNames(classOf[Question]), questions)
          val answersCsv = Export.asCsv(paramNames(classOf[Answer]), answers)
          val usersCsv = Export.asCsv(paramNames(classOf[User]), users)
          val schedulesCsv = Export.asCsv(paramNames(classOf[Schedule]), schedules)

          val files = Map(
            "studies.csv" -> studyCsv,
            "questionnaires.csv" -> questionnairesCsv,
            "questions.csv" -> questionsCsv,
            "answers.csv" -> answersCsv,
            "users.csv" -> usersCsv,
            "schedules.csv" -> schedulesCsv
          )
          Ok(Export.zip(files))
      }
    }
  }

  private def paramNames[T <: Product](caseClass: Class[T]): Seq[String] = {
    caseClass.getDeclaredFields.map(_.getName)
  }
}
