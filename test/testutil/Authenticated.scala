package testutil

import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import auth.AuthService
import auth.roles.Role
import auth.roles.Role.Role
import models.User
import models.UserRepository
import org.scalatest.BeforeAndAfterAll
import org.scalatest.TestSuite
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HttpConfiguration
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.typedmap.TypedMap
import play.api.mvc.Headers
import play.api.mvc.Result
import play.api.mvc.request.DefaultRequestFactory
import play.api.mvc.request.RequestFactory
import play.api.test.FakeRequest
import play.api.test.FakeRequestFactory
import play.api.test.Helpers._
import play.api.test.Injecting

trait Authenticated extends BeforeAndAfterAll with GuiceOneAppPerSuite with Injecting with TestUtils {
  this: TestSuite =>

  val TestPassword = "12345678"

  private val authService = inject[AuthService]
  private val userRepository = inject[UserRepository]

  protected var baseUser: User = _
  protected var researchUser: User = _
  protected var adminUser: User = _

  private var baseToken: String = _
  private var researcherToken: String = _
  private var adminToken: String = _

  private def addToken(headers: Headers, role: Option[Role]): Headers = {
    headers.add(("X-Auth-Token", role match {
      case None => baseToken
      case Some(Role.Admin) => adminToken
      case Some(Role.Researcher) => researcherToken
    }))
  }

  private val TimeoutSeconds = 5
  private val Timeout = Duration(TimeoutSeconds, TimeUnit.SECONDS)

  override def beforeAll(): Unit = {
    super.beforeAll()

    Await.result(for {
      baseUser <- registerTestUser("test_base")
      researchUser <- registerTestUser("test_researcher", Some(Role.Researcher))
      adminUser <- registerTestUser("test_admin", Some(Role.Admin))
    } yield {
      this.baseUser = baseUser
      this.researchUser = researchUser
      this.adminUser = adminUser

      baseToken = logInTestUser("test_base")
      researcherToken = logInTestUser("test_researcher")
      adminToken = logInTestUser("test_admin")
    }, Timeout)
  }

  private def registerTestUser(name: String, role: Option[Role] = None): Future[User] = {
    // XXX: Unregistering in afterAll would be cleaner, but doesn't work because of threading issues
    deleteTestUser(name).flatMap({ _ =>
      authService.register(name, TestPassword)
        .map(user => {
          userRepository.setRole(name, role)
          user
        })
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
    body: Option[JsValue] = None,
    headers: Headers = Headers(),
    role: Option[Role] = None
  )(implicit implicitRole: Role = null): Future[Result] = {
    doRequest(httpMethod, target, body, addToken(headers, if (implicitRole != null) Some(implicitRole) else role))
  }

  // XXX: The following is a bit hacky
  class AuthenticatedFakeRequestFactory(requestFactory: RequestFactory)(implicit val role: Option[Role] = None) extends FakeRequestFactory(requestFactory) {

    def apply(role: Role): AuthenticatedFakeRequestFactory = {
      new AuthenticatedFakeRequestFactory(requestFactory)(Some(role))
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
