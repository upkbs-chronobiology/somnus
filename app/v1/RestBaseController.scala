package v1

import javax.inject.Inject

import play.api.http.FileMimeTypes
import play.api.i18n.I18nSupport
import play.api.i18n.Langs
import play.api.i18n.MessagesApi
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import play.api.mvc.DefaultActionBuilder
import play.api.mvc.PlayBodyParsers

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
}
