package testutil

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.Awaitable
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import org.scalatest.TestSuite
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.libs.json.JsValue
import play.api.mvc.Headers
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.route

trait TestUtils extends GuiceOneAppPerSuite {
  this: TestSuite =>

  implicit val app: Application

  protected def doRequest(
    httpMethod: String,
    target: String,
    body: Option[JsValue] = None,
    headers: Headers = Headers()
  ): Future[Result] = {
    val base = FakeRequest(httpMethod, target).withBody(body.orNull)

    val mergedHeaders = base.headers.add(headers.toSimpleMap.toList: _*)
    val request = base.withHeaders(mergedHeaders)

    route(app, request).get
  }

  protected def doSync[T](action: Awaitable[T]): T = {
    Await.result(action, Duration(2, TimeUnit.SECONDS))
  }
}
