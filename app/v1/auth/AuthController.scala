package v1.auth

import javax.inject.Inject

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import auth.AuthService
import auth.DefaultEnv
import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import models.UserService
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.libs.json.Json
import v1.RestBaseController
import v1.RestControllerComponents

case class SignUpFormData(name: String, password: String)

object SignUpForm {
  private val PasswordMinLength = 8

  val form = Form(
    mapping(
      "name" -> nonEmptyText,
      "password" -> nonEmptyText.verifying(minLength(PasswordMinLength))
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
  credentialsProvider: CredentialsProvider,
  silhouette: Silhouette[DefaultEnv],
  authService: AuthService
)(implicit ec: ExecutionContext)
  extends RestBaseController(rcc) {

  def signUp = Action.async { implicit request =>
    SignUpForm.form.bindFromRequest.fold(
      badForm => Future.successful(BadRequest(badForm.errorsAsJson)), // XXX: More info?
      formData => {
        authService.register(formData.name, formData.password)
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
          userService.retrieve(loginInfo).flatMap {
            case None => Future.successful(credentialsWrong) // user not found
            case Some(_) => for {
              authenticator <- environment.authenticatorService.create(loginInfo)
              value <- environment.authenticatorService.init(authenticator)
              result <- environment.authenticatorService.embed(value, Ok("Login successful"))
            } yield result
          }
        } recover {
          // TODO: Proper logging
          case _: Exception => credentialsWrong
        }
      }
    )
  }
}
