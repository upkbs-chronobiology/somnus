package v1.auth

import javax.inject.Inject

import auth.DefaultEnv
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.{Credentials, PasswordHasher}
import com.mohiva.play.silhouette.api.{Environment, LoginInfo, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.BearerTokenAuthenticator
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import models.{User, UserRepository, UserService}
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.libs.json.Json
import v1.{RestBaseController, RestControllerComponents}

import scala.concurrent.{ExecutionContext, Future}

case class SignUpFormData(name: String, password: String)

object SignUpForm {
  val form = Form(
    mapping(
      "name" -> nonEmptyText,
      "password" -> nonEmptyText.verifying(minLength(8))
    )(SignUpFormData.apply)(SignUpFormData.unapply)
  )
}

case class LoginFormData(name: String, password: String)

object LoginForm {
  val form = Form(
    mapping(
      "name" -> nonEmptyText,
      "password" -> nonEmptyText
    )(SignUpFormData.apply)(SignUpFormData.unapply)
  )
}

class AuthController @Inject()(
  val environment: Environment[DefaultEnv],
  rcc: RestControllerComponents,
  userService: UserService,
  authInfoRepository: AuthInfoRepository,
  credentialsProvider: CredentialsProvider,
  passwordHasher: PasswordHasher,
  userRepository: UserRepository,
  silhouette: Silhouette[DefaultEnv]
)(implicit ec: ExecutionContext)
  extends RestBaseController(rcc) {

  def signUp = Action.async { implicit request =>
    SignUpForm.form.bindFromRequest.fold(
      badForm => Future.successful(BadRequest(badForm.errorsAsJson)), // XXX: More info?
      formData => {
        val loginInfo = LoginInfo(credentialsProvider.id, formData.name)
        userService.retrieve(loginInfo).flatMap {
          case Some(_) => Future.successful(BadRequest("user already exists"))
          case None =>
            for {
              user <- userRepository.create(User(0, loginInfo.providerKey, None))
              _ <- authInfoRepository.add(loginInfo, passwordHasher.hash(formData.password))
            } yield
              // TODO: Log in?
              Created(Json.toJson(user))
        }
      }
    )
  }

  def logIn = silhouette.UserAwareAction.async { implicit request =>
    val credentialsWrong = BadRequest("Credentials are not correct")

    LoginForm.form.bindFromRequest.fold(
      badForm => Future.successful(BadRequest("Login failed")),
      formData => {
        val credentials = Credentials(formData.name, formData.password)
        credentialsProvider.authenticate(credentials).flatMap { loginInfo =>
          userService.retrieve(loginInfo).flatMap {
            case None =>
              Future.successful(credentialsWrong) // no user
            case Some(_) => for {
              authenticator <- environment.authenticatorService.create(loginInfo)
              value <- environment.authenticatorService.init(authenticator)
              result <- environment.authenticatorService.embed(value, Ok("Login successful"))
            } yield result
          }
        } recover {
          case _: Exception => credentialsWrong
        }
      }
    )
  }
}
