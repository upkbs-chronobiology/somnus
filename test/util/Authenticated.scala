package util

import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

import auth.AuthService
import auth.roles.Role
import auth.roles.Role.Role
import models.UserRepository
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

  val TestPassword = "12345678"

  private val authService = inject[AuthService]
  private val userRepository = inject[UserRepository]

  private var baseToken: String = _
  private var researcherToken: String = _
  private var adminToken: String = _

  private def addToken(headers: Headers, role: Role): Headers = {
    headers.add(("X-Auth-Token", role match {
      case Role.Admin => adminToken
      case Role.Researcher => researcherToken
      case _ => baseToken
    }))
  }

  private val Timeout = Duration(5, TimeUnit.SECONDS)

  override def beforeAll(): Unit = {
    super.beforeAll()

    Await.result(for {
      _ <- registerTestUser("test_base")
      _ <- registerTestUser("test_researcher", Role.Researcher)
      _ <- registerTestUser("test_admin", Role.Admin)
    } yield {
      baseToken = logInTestUser("test_base")
      researcherToken = logInTestUser("test_researcher")
      adminToken = logInTestUser("test_admin")
    }, Timeout)
  }

  private def registerTestUser(name: String, role: Role = null): Future[_] = {
    // XXX: Unregistering in afterAll would be cleaner, but doesn't work because of threading issues
    deleteTestUser(name).flatMap({ _ =>
      authService.register(name, TestPassword)
        .map(_ => userRepository.setRole(name, role))
    })
  }

  private def deleteTestUser(name: String): Future[_] = {
    authService.unregister(name)
  }

  private def logInTestUser(name: Json.JsValueWrapper): String = {
    val user = Json.obj(
      "name" -> name,
      "password" -> TestPassword
    )
    val request = FakeRequest(POST, "/v1/auth/login").withBody(user)
    val result = route(app, request).get
    header("X-Auth-Token", result)
      .getOrElse(throw new IllegalStateException("Auth token missing from login response"))
  }

  def doAuthenticatedRequest(
    httpMethod: String,
    target: String,
    body: JsValue = null,
    headers: Headers = Headers(),
    role: Role = null
  ): Future[Result] = {
    doRequest(httpMethod, target, body, addToken(headers, role))
  }

  // XXX: The following is a bit hacky
  class AuthenticatedFakeRequestFactory(requestFactory: RequestFactory)(implicit val role: Role = null) extends FakeRequestFactory(requestFactory) {

    def apply(role: Role): AuthenticatedFakeRequestFactory = {
      new AuthenticatedFakeRequestFactory(requestFactory)(role)
    }

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
      super.apply(method, uri, addToken(headers, role), body, remoteAddress, version, id, tags, secure, clientCertificateChain, attrs)
    }
  }

  object AuthenticatedFakeRequest extends AuthenticatedFakeRequestFactory(new DefaultRequestFactory(HttpConfiguration()))

}
