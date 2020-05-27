package v1

import play.api.i18n.MessagesApi
import play.api.mvc._

trait RestRequestHeader extends MessagesRequestHeader with PreferredMessagesProvider

class RestRequest[A](request: Request[A], val messagesApi: MessagesApi)
    extends WrappedRequest(request)
    with RestRequestHeader
