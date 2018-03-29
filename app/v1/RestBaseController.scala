package v1

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import _root_.auth.DefaultEnv
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import javax.inject.Inject
import play.api.data.Form
import play.api.http.FileMimeTypes
import play.api.i18n.I18nSupport
import play.api.i18n.Langs
import play.api.i18n.MessagesApi
import play.api.mvc.AnyContent
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import play.api.mvc.DefaultActionBuilder
import play.api.mvc.PlayBodyParsers
import play.api.mvc.Result
import util.JsonError

case class RestControllerComponents @Inject()(
  actionBuilder: DefaultActionBuilder,
  parsers: PlayBodyParsers,
  messagesApi: MessagesApi,
  langs: Langs,
  fileMimeTypes: FileMimeTypes,
  executionContext: scala.concurrent.ExecutionContext
) extends ControllerComponents

class RestBaseController @Inject()(rcc: RestControllerComponents) extends BaseController with I18nSupport {

  override protected def controllerComponents: ControllerComponents = rcc

  protected def digestForm[T](form: Form[T], validCallback: T => Future[Result])(
    implicit request: SecuredRequest[DefaultEnv, AnyContent]
  ): Future[Result] = {
    form.bindFromRequest.fold(
      badForm => Future.successful(BadRequest(badForm.errorsAsJson)),
      formData => validCallback(formData).recover {
        case e: IllegalArgumentException => BadRequest(JsonError(e.getMessage))
      }
    )
  }
}
