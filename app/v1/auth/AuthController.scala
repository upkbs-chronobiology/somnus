package v1.auth

import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import auth.AuthService
import auth.DefaultEnv
import auth.roles.ForEditors
import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import com.mohiva.play.silhouette.impl.exceptions.InvalidPasswordException
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import exceptions.ItemNotFoundException
import javax.inject.Inject
import models.PwResetsRepository
import models.UserRepository
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.Mapping
import play.api.data.validation.Constraints._
import play.api.libs.json.Json
import util.JsonError
import util.JsonSuccess
import util.Logging
import v1.RestBaseController
import v1.RestControllerComponents

case class SignUpFormData(name: String, password: String)

object SignUpForm {
  private val PasswordMinLength = 8
  val passwordField: Mapping[String] = nonEmptyText.verifying(minLength(PasswordMinLength))

  val form = Form(
    mapping(
      "name" -> nonEmptyText,
      "password" -> passwordField
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

case class PwResetFormData(password: String)

object PwResetForm {
  val form = Form(
    mapping(
      "password" -> SignUpForm.passwordField
    )(PwResetFormData.apply)(PwResetFormData.unapply)
  )
}

class AuthController @Inject()(
  val environment: Environment[DefaultEnv],
  rcc: RestControllerComponents,
  userRepo: UserRepository,
  pwResetsRepo: PwResetsRepository,
  credentialsProvider: CredentialsProvider,
  silhouette: Silhouette[DefaultEnv],
  authService: AuthService
)(implicit ec: ExecutionContext)
  extends RestBaseController(rcc) with Logging {

  def signUp = Action.async { implicit request =>
    SignUpForm.form.bindFromRequest.fold(
      badForm => Future.successful(BadRequest(badForm.errorsAsJson)), // XXX: More info?
      formData => {
        authService.register(formData.name, Some(formData.password))
          .map(user => Created(Json.toJson(user)))
          .recover {
            case e: Exception => BadRequest(e.getMessage)
          }
      }
    )
  }

  def logIn = Action.async { implicit request =>
    val credentialsWrong = BadRequest("Credentials are not correct")

    LoginForm.form.bindFromRequest.fold(
      badForm => Future.successful(BadRequest("Login failed")),
      formData => {
        val credentials = Credentials(formData.name, formData.password)
        credentialsProvider.authenticate(credentials).flatMap { loginInfo =>
          userRepo.retrieve(loginInfo).flatMap {
            case None => Future.successful(credentialsWrong) // user not found
            case Some(user) => for {
              authenticator <- environment.authenticatorService.create(loginInfo)
              value <- environment.authenticatorService.init(authenticator)
              result <- environment.authenticatorService.embed(value, Ok(Json.toJson(user)))
            } yield result
          }
        } recover {
          case _: IllegalArgumentException => credentialsWrong
          case _: IdentityNotFoundException => credentialsWrong
          case _: InvalidPasswordException => credentialsWrong
          case e: Exception =>
            e.printStackTrace()
            logger.error("Exception during login attempt", e)
            credentialsWrong
        }
      }
    )
  }

  def createResetToken(userId: Long) = silhouette.SecuredAction(ForEditors).async {
    val tomorrow = Timestamp.from(Instant.now().plus(Duration.ofDays(1)))
    authService.generateResetToken(userId, tomorrow)
      .map(pwReset => Created(Json.toJson(pwReset)))
      .recover {
        case e: ItemNotFoundException => NotFound(JsonError(e.getMessage))
      }
  }

  def getUserForToken(token: String) = Action.async { implicit request =>
    pwResetsRepo.getByToken(token) flatMap {
      case None => Future.successful(NotFound(JsonError("Token not valid")))
      case Some(pwReset) => userRepo.get(pwReset.userId) map {
        case None => NotFound(JsonError("User for token does not exist"))
        case Some(user) => Ok(Json.toJson(user))
      }
    }
  }

  def resetPassword(token: String) = Action.async { implicit request =>
    PwResetForm.form.bindFromRequest().fold(
      badForm => Future.successful(BadRequest(badForm.errorsAsJson)),
      formData =>
        authService.resetPassword(token, formData.password)
          .map(_ => Ok(JsonSuccess("Password successfully reset")))
          .recover {
            case e: ItemNotFoundException =>
              NotFound(JsonError(e.getMessage))
            case e: IllegalArgumentException =>
              BadRequest(JsonError(e.getMessage))
          }
    )
  }
}
