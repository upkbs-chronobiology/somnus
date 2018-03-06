package v1.study

import auth.roles.Role
import models.Study
import models.StudyRepository
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.JsArray
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.Injecting
import testutil.Authenticated
import testutil.FreshDatabase
import testutil.TestUtils

class StudyControllerSpec extends PlaySpec
  with GuiceOneAppPerSuite with Injecting with FreshDatabase with TestUtils with Authenticated {

  "StudyController" when {
    "not logged in" should {
      "reject requests" in {
        status(doRequest(GET, "/v1/studies")) must equal(401)
        status(doRequest(GET, "/v1/studies/99")) must equal(401)
        status(doRequest(POST, "/v1/studies", Some(studyJson("Foo Study")))) must equal(401)
        status(doRequest(PUT, "/v1/studies/88", Some(studyJson("Bar Study")))) must equal(401)
        status(doRequest(DELETE, "/v1/studies/77")) must equal(401)
      }
    }

    "logged in as basic user" should {
      "allow reading" in {
        val studyRepository = inject[StudyRepository]
        val study = doSync(studyRepository.create(Study(0, "test study")))

        val readStudy = contentAsJson(doAuthenticatedRequest(GET, s"/v1/studies/${study.id}"))
        readStudy("name").as[String] must equal("test study")

        val readStudies = contentAsJson(doAuthenticatedRequest(GET, "/v1/studies"))
        readStudies.as[JsArray].value.length must equal(1)

        doSync(studyRepository.delete(study.id))
      }

      "reject altering" in {
        status(doAuthenticatedRequest(POST, "/v1/studies", Some(studyJson("Blabla Study")))) must equal(403)
        status(doAuthenticatedRequest(PUT, "/v1/studies/88", Some(studyJson("Yada Study")))) must equal(403)
        status(doAuthenticatedRequest(DELETE, "/v1/studies/77")) must equal(403)
      }
    }

    "logged in as researcher" should {
      implicit val _ = Role.Researcher

      "allow all CRUD operations" in {
        contentAsJson(doAuthenticatedRequest(GET, "/v1/studies")).as[JsArray].value.length must equal(0)

        val creationResult = doAuthenticatedRequest(POST, "/v1/studies", Some(studyJson("Foo Bar")))
        status(creationResult) must equal(201)
        val createdId = contentAsJson(creationResult).apply("id").as[Long]

        contentAsJson(doAuthenticatedRequest(GET, "/v1/studies")).as[JsArray].value.length must equal(1)
        contentAsJson(doAuthenticatedRequest(GET, s"/v1/studies/$createdId")).apply("name").as[String] must equal("Foo Bar")

        status(doAuthenticatedRequest(PUT, s"/v1/studies/$createdId", Some(studyJson("My New Name")))) must equal(200)

        contentAsJson(doAuthenticatedRequest(GET, s"/v1/studies/$createdId")).apply("name").as[String] must equal("My New Name")

        status(doAuthenticatedRequest(DELETE, s"/v1/studies/$createdId", Some(studyJson("My New Name")))) must equal(200)

        contentAsJson(doAuthenticatedRequest(GET, "/v1/studies")).as[JsArray].value.length must equal(0)
      }

      "reject updates with non-existent ids" in {
        status(doAuthenticatedRequest(PUT, "/v1/studies/666", Some(studyJson("Out Of Ideas")))) must equal(400)
      }
    }
  }

  private def studyJson(name: String): JsValue = {
    Json.obj(
      "name" -> name
    )
  }

}
