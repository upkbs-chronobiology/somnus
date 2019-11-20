package auth.acl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import auth.roles.Role
import com.mohiva.play.silhouette.api.Authorization
import com.mohiva.play.silhouette.impl.authenticators.BearerTokenAuthenticator
import models.AccessLevel.AccessLevel
import models.StudyAccessRepository
import models.User
import play.api.mvc.Request

case class WithUserAccess(targetUserId: Long, level: AccessLevel) extends Authorization[User, BearerTokenAuthenticator] {

  override def isAuthorized[B](user: User, authenticator: BearerTokenAuthenticator)
    (implicit request: Request[B]): Future[Boolean] = {
    user match {
      case _ if user.id == targetUserId => Future.successful(true)
      case _ if user.hasRole(Role.Admin) => Future.successful(true)
      case _ =>
        // TODO: Implement
        Future.successful(false)
    }
  }
}

// XXX: Cleaner solution than implicitly passing StudyAccessRepository?
case class WithStudyAccess(studyId: Long, level: AccessLevel)(implicit studyAccessRepo: StudyAccessRepository)
  extends Authorization[User, BearerTokenAuthenticator] {

  override def isAuthorized[B](user: User, authenticator: BearerTokenAuthenticator)
    (implicit request: Request[B]): Future[Boolean] = {
    user match {
      case _ if user.hasRole(Role.Admin) => Future.successful(true)
      case _ =>
        studyAccessRepo.read(user.id, studyId).map(_.exists(_.level >= level))
    }
  }
}
