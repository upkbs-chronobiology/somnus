package util

import java.util.concurrent.TimeUnit

import org.scalatest.TestSuite
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.libs.json.JsValue
import play.api.mvc.{Headers, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.route

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Awaitable, Future}

trait TestUtils extends GuiceOneAppPerSuite {
  this: TestSuite =>

  implicit val app: Application

  protected def doRequest(
    httpMethod: String,
    target: String,
    body: JsValue = null,
    headers: Headers = Headers()
  ): Future[Result] = {
    val base = FakeRequest(httpMethod, target).withBody(body)
    val mergedHeaders = base.headers.add(headers.toSimpleMap.toList: _*)
    val request = base.withHeaders(mergedHeaders)

    route(app, request).get
  }

  protected def doSync[T](action: Awaitable[T]): T = {
    Await.result(action, Duration(1, TimeUnit.SECONDS))
  }
}
