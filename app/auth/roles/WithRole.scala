package auth.roles

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import auth.roles.Role.Role
import com.mohiva.play.silhouette.api.Authorization
import com.mohiva.play.silhouette.impl.authenticators.BearerTokenAuthenticator
import models.User
import play.api.mvc.Request
import scalaz.Scalaz._

/** Authorization implementation restricting based on roles.
  *
  * @param roles Roles enabling this endpoint. The ruling is disjunctive,
  *              so at least one of the roles needs to be present.
  */
case class WithRole(roles: Role*) extends Authorization[User, BearerTokenAuthenticator] {

  override def isAuthorized[B](user: User, authenticator: BearerTokenAuthenticator)
    (implicit request: Request[B]): Future[Boolean] = {
    Future.successful(roles.exists(_.toString === user.role.getOrElse("")))
  }
}

class WithEditorRole extends WithRole(Role.Admin, Role.Researcher)

class WithAdminRole extends WithRole(Role.Admin)

class ForAnyEditorOrUser(userId: Long) extends WithEditorRole {

  override def isAuthorized[B](identity: User, authenticator: BearerTokenAuthenticator)
    (implicit request: Request[B]): Future[Boolean] = {
    super.isAuthorized[B](identity, authenticator).map(authorized => authorized || identity.id === userId)
  }
}

object ForAnyEditorOrUser {
  def apply(userId: Long): ForAnyEditorOrUser = new ForAnyEditorOrUser(userId)
}

object ForEditors extends WithEditorRole

object ForAdmins extends WithAdminRole
