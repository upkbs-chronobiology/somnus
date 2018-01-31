package auth.roles

import auth.roles.Role.Role
import com.mohiva.play.silhouette.api.Authorization
import com.mohiva.play.silhouette.impl.authenticators.BearerTokenAuthenticator
import models.User
import play.api.mvc.Request

import scala.concurrent.Future

case class WithRole(role: Role) extends Authorization[User, BearerTokenAuthenticator] {

  override def isAuthorized[B](user: User, authenticator: BearerTokenAuthenticator)
    (implicit request: Request[B]): Future[Boolean] = {
    Future.successful(user.role.orNull == role.toString)
  }
}
