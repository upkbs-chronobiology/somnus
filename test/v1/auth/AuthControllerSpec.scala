package v1.auth

import models.{PasswordRepository, UserRepository}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.Injecting
import util.TestUtils

import scala.concurrent.ExecutionContext.Implicits.global

class AuthControllerSpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with TestUtils {

  private val userRepository = inject[UserRepository]
  private val passwordRepository = inject[PasswordRepository]

  "AuthController sign-up endpoint" should {

    "reject short passwords" in {
      val response = doRequest(POST, "/v1/auth/signup", signUpJson("Jeff Goldberg", "imagoat"))
      status(response) must equal(BAD_REQUEST)

      userRepository.listAll().map(_ mustBe empty)
    }

    "create new user and password" in {
      val response = doRequest(POST, "/v1/auth/signup", signUpJson("John Karcis", "notagoat"))
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
      val response = doRequest(POST, "/v1/auth/login", signUpJson("Santa", "northpoleiscold"))
      status(response) must equal(BAD_REQUEST)
    }

    "reject wrong password" in {
      val response = doRequest(POST, "/v1/auth/login", signUpJson("John Karcis", "ThisIsAWrongPassword"))
      status(response) must equal(BAD_REQUEST)
    }

    "accept correct credentials" in {
      val response = doRequest(POST, "/v1/auth/login", signUpJson("John Karcis", "notagoat"))
      status(response) must equal(OK)
      header("X-Auth-Token", response).get.length must equal(256)
    }
  }

  private def signUpJson(name: String, password: String) = Json.obj(
    "name" -> name,
    "password" -> password
  )
}
