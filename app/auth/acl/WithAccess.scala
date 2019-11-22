package auth.acl

import scala.concurrent.Future

import com.mohiva.play.silhouette.api.Authorization
import com.mohiva.play.silhouette.impl.authenticators.BearerTokenAuthenticator
import models.AccessLevel.AccessLevel
import models.User
import play.api.mvc.Request

case class WithUserAccess(targetUserId: Long, level: AccessLevel)(implicit accessRules: AccessRules) extends Authorization[User, BearerTokenAuthenticator] {

  override def isAuthorized[B](user: User, authenticator: BearerTokenAuthenticator)
    (implicit request: Request[B]): Future[Boolean] = {
    accessRules.mayAccessUser(user, targetUserId, level)
  }
}

case class WithStudyAccess(studyId: Long, level: AccessLevel)(implicit accessRules: AccessRules)
  extends Authorization[User, BearerTokenAuthenticator] {

  override def isAuthorized[B](user: User, authenticator: BearerTokenAuthenticator)
    (implicit request: Request[B]): Future[Boolean] = {
    accessRules.mayAccessStudy(user, studyId, level)
  }
}

case class WithQuestionnaireAccess(questionnaireId: Long, level: AccessLevel)
  (implicit accessRules: AccessRules)
  extends Authorization[User, BearerTokenAuthenticator] {

  override def isAuthorized[B](user: User, authenticator: BearerTokenAuthenticator)
    (implicit request: Request[B]): Future[Boolean] = {
    accessRules.mayAccessQuestionnaire(user, questionnaireId, level)
  }
}

case class WithQuestionAccess(questionId: Long, level: AccessLevel)
  (implicit accessRules: AccessRules)
  extends Authorization[User, BearerTokenAuthenticator] {

  override def isAuthorized[B](user: User, authenticator: BearerTokenAuthenticator)
    (implicit request: Request[B]): Future[Boolean] = {
    accessRules.mayAccessQuestion(user, questionId, level)
  }
}

case class WithAnswerAccess(answerId: Long, level: AccessLevel)
  (implicit accessRules: AccessRules)
  extends Authorization[User, BearerTokenAuthenticator] {

  override def isAuthorized[B](user: User, authenticator: BearerTokenAuthenticator)
    (implicit request: Request[B]): Future[Boolean] = {
    accessRules.mayAccessAnswer(user, answerId, level)
  }
}

case class WithScheduleAccess(scheduleId: Long, level: AccessLevel)(implicit accessRules: AccessRules)
  extends Authorization[User, BearerTokenAuthenticator] {

  override def isAuthorized[B](user: User, authenticator: BearerTokenAuthenticator)
    (implicit request: Request[B]): Future[Boolean] = {
    accessRules.mayAccessSchedule(user, scheduleId, level)
  }
}
