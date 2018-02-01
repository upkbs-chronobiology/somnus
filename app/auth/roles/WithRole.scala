package auth.roles

import auth.roles.Role.Role
import com.mohiva.play.silhouette.api.Authorization
import com.mohiva.play.silhouette.impl.authenticators.BearerTokenAuthenticator
import models.User
import play.api.mvc.Request

import scala.concurrent.Future
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

object ForEditors extends WithRole(Role.Admin, Role.Researcher)
