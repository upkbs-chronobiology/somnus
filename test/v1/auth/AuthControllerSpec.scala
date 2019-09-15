package v1.auth

import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

import scala.concurrent.ExecutionContext.Implicits.global

import auth.AuthService
import auth.roles.Role
import models.PasswordRepository
import models.UserRepository
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.Injecting
import testutil.Authenticated
import testutil.TestUtils

class AuthControllerSpec extends PlaySpec
  with GuiceOneAppPerSuite with Injecting with TestUtils with Authenticated {

  private val userRepository = inject[UserRepository]
  private val passwordRepository = inject[PasswordRepository]
  private val authService = inject[AuthService]

  "AuthController sign-up endpoint" should {

    "reject short passwords" in {
      val response = doRequest(POST, "/v1/auth/signup", Some(signUpJson("Jeff Goldberg", "imagoat")))
      status(response) must equal(BAD_REQUEST)

      userRepository.listAll().map(_ mustBe empty)
    }

    "create new user and password" in {
      val response = doRequest(POST, "/v1/auth/signup", Some(signUpJson("John Karcis", "notagoat")))
      status(response) must equal(CREATED)

      val users = userRepository.listAll()
      users.map(_.map(_.name)).map(_ must contain("John Karcis"))
      users.map(
        _.filter(_.name == "John Karcis").map(_.passwordId.get).head
      ).flatMap(passwordRepository.get).map(_ must contain("notagoat"))
    }
  }

  "AuthController login endpoint" should {

    "reject non-existing users" in {
      val response = doRequest(POST, "/v1/auth/login", Some(signUpJson("Santa", "northpoleiscold")))
      status(response) must equal(BAD_REQUEST)
    }

    "reject wrong password" in {
      val response = doRequest(POST, "/v1/auth/login", Some(signUpJson("John Karcis", "ThisIsAWrongPassword")))
      status(response) must equal(BAD_REQUEST)
    }

    "accept correct credentials" in {
      val response = doRequest(POST, "/v1/auth/login", Some(signUpJson("John Karcis", "notagoat")))
      status(response) must equal(OK)
      header("X-Auth-Token", response).get.length must equal(256)
    }

    "accept credentials case-insensitively" in {
      val response = doRequest(POST, "/v1/auth/login", Some(signUpJson("john KARCIS", "notagoat")))
      status(response) must equal(OK)
      header("X-Auth-Token", response).get.length must equal(256)
    }
  }

  "AuthController password reset endpoint" when {
    val jeff = doSync(authService.register("Jeff Goldblum", Some("jeffjeff")))

    "not logged in" should {
      "reject generating tokens" in {
        status(doRequest(GET, s"/v1/auth/password/reset/new/${jeff.id}")) must equal(UNAUTHORIZED)
      }
    }

    "logged in as base user" should {
      "reject generating tokens" in {
        status(doAuthenticatedRequest(GET, s"/v1/auth/password/reset/new/${jeff.id}")) must equal(FORBIDDEN)
      }

      "deliver users for tokens" in {
        val tomorrow = Timestamp.from(Instant.now() plus Duration.ofDays(1))
        val token = doSync(authService.generateResetToken(jeff.id, tomorrow)).token

        val response = doAuthenticatedRequest(GET, s"/v1/auth/password/reset/$token/user")
        status(response) must equal(OK)
        contentAsJson(response).apply("name").as[String] must equal("Jeff Goldblum")
      }

      "reject short passwords" in {
        val tomorrow = Timestamp.from(Instant.now() plus Duration.ofDays(1))

        val token = doSync(authService.generateResetToken(jeff.id, tomorrow)).token
        val response = doAuthenticatedRequest(POST, s"/v1/auth/password/reset/$token", Some(pwResetJson("123")))
        status(response) must equal(BAD_REQUEST)
      }

      "change password" in {
        val tomorrow = Timestamp.from(Instant.now() plus Duration.ofDays(1))
        val token = doSync(authService.generateResetToken(jeff.id, tomorrow)).token

        val response = doAuthenticatedRequest(POST, s"/v1/auth/password/reset/$token", Some(pwResetJson("asdfasdf")))

        status(response) must equal(OK)

        val loginResponse = doRequest(POST, "/v1/auth/login", Some(signUpJson("Jeff Goldblum", "asdfasdf")))
        status(loginResponse) must equal(OK)
        header("X-Auth-Token", loginResponse).get.length must equal(256)
      }

      "create password for new accounts" in {
        val olga = doSync(authService.register("Olga Bołądź", None))

        val tomorrow = Timestamp.from(Instant.now() plus Duration.ofDays(1))
        val token = doSync(authService.generateResetToken(olga.id, tomorrow)).token

        val response = doAuthenticatedRequest(POST, s"/v1/auth/password/reset/$token", Some(pwResetJson("qwerqwer")))

        status(response) must equal(OK)

        val loginResponse = doRequest(POST, "/v1/auth/login", Some(signUpJson("Olga Bołądź", "qwerqwer")))
        status(loginResponse) must equal(OK)
        header("X-Auth-Token", loginResponse).get.length must equal(256)
      }
    }

    "logged in as researcher" should {
      implicit val _ = Role.Researcher

      "reject generating tokens for inexistent users" in {
        status(doAuthenticatedRequest(GET, "/v1/auth/password/reset/new/999")) must equal(NOT_FOUND)
      }

      "reject generating tokens for users of same or higher permission" in {
        val adi = doSync(authService.register("Adrian Admin", None))
        doSync(userRepository.setRole(adi.id, Some(Role.Admin)))
        val resi = doSync(authService.register("Reese Researcher", Some("IAMREESE")))
        doSync(userRepository.setRole(resi.id, Some(Role.Researcher)))

        status(doAuthenticatedRequest(GET, s"/v1/auth/password/reset/new/${adi.id}")) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(GET, s"/v1/auth/password/reset/new/${resi.id}")) must equal(FORBIDDEN)
      }

      "generate a token" in {
        val response = doAuthenticatedRequest(GET, s"/v1/auth/password/reset/new/${jeff.id}")

        status(response) must equal(CREATED)

        val pwReset = contentAsJson(response)
        pwReset("token").as[String].length must equal(12)
        pwReset("userId").as[Long] must equal(jeff.id)

        val expiry = pwReset("expiry").as[Long]
        expiry must be >= Instant.now().plus(Duration.ofDays(13)).toEpochMilli
        expiry must be <= Instant.now().plus(Duration.ofDays(15)).toEpochMilli
      }

      "reject unknown tokens" in {
        val response = doAuthenticatedRequest(POST, "/v1/auth/password/reset/9a8b7c6d5e", Some(pwResetJson("12345678")))
        status(response) must equal(NOT_FOUND)
      }

      "reject expired tokens" in {
        val oneHourAgo = Timestamp.from(Instant.now() minus Duration.ofHours(1))
        val token = authService.generateResetToken(jeff.id, oneHourAgo)

        val response = doAuthenticatedRequest(POST, s"/v1/auth/password/reset/$token", Some(pwResetJson("12345678")))
        status(response) must equal(BAD_REQUEST)
      }
    }

    "logged in as admin" should {
      implicit val _ = Role.Admin

      "reject generating a token for another admin" in {
        val aaron = doSync(authService.register("Aaron Admin", None))
        doSync(userRepository.setRole(aaron.id, Some(Role.Admin)))

        status(doAuthenticatedRequest(GET, s"/v1/auth/password/reset/new/${aaron.id}")) must equal(FORBIDDEN)
      }

      "generate a token for research users" in {
        val ronda = doSync(authService.register("Ronda Researcher", Some("IAMREESE")))
        doSync(userRepository.setRole(ronda.id, Some(Role.Researcher)))

        status(doAuthenticatedRequest(GET, s"/v1/auth/password/reset/new/${ronda.id}")) must equal(CREATED)
      }
    }
  }

  private def signUpJson(name: String, password: String) = Json.obj(
    "name" -> name,
    "password" -> password
  )

  private def pwResetJson(password: String) = Json.obj("password" -> password)
}
