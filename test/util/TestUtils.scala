package util

import java.util.concurrent.TimeUnit

import play.api.Application
import play.api.libs.json.JsValue
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.route

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Awaitable, Future}

trait TestUtils {

  implicit val app: Application

  protected def doRequest(httpMethod: String, target: String, body: JsValue = null): Future[Result] = {
    val request = FakeRequest(httpMethod, target).withBody(body)
    route(app, request).get
  }

  protected def doSync[T](action: Awaitable[T]): T = {
    Await.result(action, Duration(1, TimeUnit.SECONDS))
  }
}
