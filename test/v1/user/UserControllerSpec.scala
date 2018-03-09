package v1.user

import auth.AuthService
import auth.roles.Role
import models.Study
import models.StudyRepository
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.JsArray
import play.api.test.Helpers._
import play.api.test.Injecting
import testutil.Authenticated

class UserControllerSpec extends PlaySpec
  with GuiceOneAppPerSuite with Injecting with BeforeAndAfterAll with Authenticated {

  val donald = doSync(inject[AuthService].register("Donald Duck", "00112233"))

  override def beforeAll(): Unit = {
    super.beforeAll()

    val studyRepo = inject[StudyRepository]
    val study = doSync(studyRepo.create(Study(0, "Test Study")))

    doSync(studyRepo.addParticipant(study.id, donald.id))
    doSync(studyRepo.addParticipant(study.id, baseUser.id))
  }

  "UserController" when {
    "not logged in" should {
      "reject listing users" in {
        status(doRequest(GET, "/v1/users")) must equal(401)
      }
    }

    "logged in as basic user" should {
      "reject listing users" in {
        status(doAuthenticatedRequest(GET, "/v1/users")) must equal(403)
      }

      "reject listing studies of other users" in {
        status(doAuthenticatedRequest(GET, s"/v1/users/${donald.id}/studies")) must equal(403)
      }

      "list studies of current user" in {
        val response = doAuthenticatedRequest(GET, s"/v1/users/${baseUser.id}/studies")

        status(response) must equal(200)

        val list = contentAsJson(response).as[JsArray].value
        list.length must equal(1)
        list.head.apply("name").as[String] must equal("Test Study")
      }
    }

    "logged in as editor" should {
      implicit val _ = Role.Researcher

      "list users" in {
        val response = doAuthenticatedRequest(GET, "/v1/users")

        status(response) must equal(200)

        val list = contentAsJson(response).as[JsArray].value
        list.length must be >= 0
        list.map(_ ("name").as[String]) must contain(donald.name)
      }

      "list studies of other users" in {
        val response = doAuthenticatedRequest(GET, s"/v1/users/${donald.id}/studies")

        status(response) must equal(200)

        val list = contentAsJson(response).as[JsArray].value
        list.length must equal(1)
        list.head.apply("name").as[String] must equal("Test Study")
      }
    }
  }
}