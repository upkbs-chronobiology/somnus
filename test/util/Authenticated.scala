package util

import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

import auth.AuthService
import org.scalatest.{BeforeAndAfterAll, TestSuite}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HttpConfiguration
import play.api.libs.json.{JsValue, Json}
import play.api.libs.typedmap.TypedMap
import play.api.mvc.request.{DefaultRequestFactory, RequestFactory}
import play.api.mvc.{Headers, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, FakeRequestFactory, Injecting}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

trait Authenticated extends BeforeAndAfterAll with GuiceOneAppPerSuite with Injecting with TestUtils {
  this: TestSuite =>

  private val authService = inject[AuthService]

  private var authToken: String = _

  private def addToken(headers: Headers): Headers = headers.add(("X-Auth-Token", authToken))

  private val Timeout = Duration(5, TimeUnit.SECONDS)

  override def beforeAll(): Unit = {
    super.beforeAll()
    // XXX: Isn't there a cleaner solution?
    Await.result(registerTestUser().map(_ => {
      logInTestUser()
    }), Timeout)
  }

  private def registerTestUser(): Future[_] = {
    // XXX: Unregistering in afterAll would be cleaner, but doesn't work because of threading issues
    deleteTestUser().flatMap({ _ =>
      authService.register("testuser", "12345678")
    })
  }

  private def deleteTestUser(): Future[_] = {
    authService.unregister("testuser")
  }

  private def logInTestUser(): Unit = {
    val user = Json.obj(
      "name" -> "testuser",
      "password" -> "12345678"
    )
    val request = FakeRequest(POST, "/v1/auth/login").withBody(user)
    val result = route(app, request).get
    authToken = header("X-Auth-Token", result)
      .getOrElse(throw new IllegalStateException("Auth token missing from login response"))
  }

  def doAuthenticatedRequest(
    httpMethod: String,
    target: String,
    body: JsValue = null,
    headers: Headers = Headers()
  ): Future[Result] = {
    doRequest(httpMethod, target, body, addToken(headers))
  }

  // XXX: The following is a bit hacky
  class AuthenticatedFakeRequestFactory(requestFactory: RequestFactory) extends FakeRequestFactory(requestFactory) {
    override def apply[A](
      method: String,
      uri: String,
      headers: Headers,
      body: A,
      remoteAddress: String,
      version: String,
      id: Long,
      tags: Map[String, String],
      secure: Boolean,
      clientCertificateChain: Option[Seq[X509Certificate]],
      attrs: TypedMap
    ): FakeRequest[A] = {
      super.apply(method, uri, addToken(headers), body, remoteAddress, version, id, tags, secure, clientCertificateChain, attrs)
    }
  }

  object AuthenticatedFakeRequest extends AuthenticatedFakeRequestFactory(new DefaultRequestFactory(HttpConfiguration()))

}
