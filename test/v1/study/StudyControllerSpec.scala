package v1.study

import auth.AuthService
import auth.roles.Role
import models.Questionnaire
import models.QuestionnaireRepository
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
      "reject reading" in {
        val studyRepository = inject[StudyRepository]
        val study = doSync(studyRepository.create(Study(0, "test study")))

        status(doAuthenticatedRequest(GET, "/v1/studies")) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(GET, s"/v1/studies/${study.id}")) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(GET, s"/v1/studies/${study.id}/questionnaires")) must equal(FORBIDDEN)

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

      "allow reading" in {
        val studyRepository = inject[StudyRepository]
        val study = doSync(studyRepository.create(Study(0, "test study")))

        val readStudy = contentAsJson(doAuthenticatedRequest(GET, s"/v1/studies/${study.id}"))
        readStudy("name").as[String] must equal("test study")

        val readStudies = contentAsJson(doAuthenticatedRequest(GET, "/v1/studies"))
        readStudies.as[JsArray].value.length must equal(1)

        doSync(studyRepository.delete(study.id))
      }

      "deliver questionnaires for a study" in {
        val questionnaires = inject[QuestionnaireRepository]

        val studyRepository = inject[StudyRepository]
        val study = doSync(studyRepository.create(Study(0, "Xyz Study")))

        val questionnaireA = doSync(questionnaires.create(Questionnaire(0, "Questionnaire A", Some(study.id))))
        val questionnaireB = doSync(questionnaires.create(Questionnaire(0, "Questionnaire B", Some(study.id))))

        val response = doAuthenticatedRequest(GET, s"/v1/studies/${study.id}/questionnaires")
        status(response) must equal(200)

        val list = contentAsJson(response).as[JsArray].value
        list.length must equal(2)
        list.map(item => item("name").as[String]) must contain allOf("Questionnaire A", "Questionnaire B")

        doSync(questionnaires.delete(questionnaireA.id))
        doSync(questionnaires.delete(questionnaireB.id))
        doSync(studyRepository.delete(study.id))
      }

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

      "list correct participants after adding and removing" in {
        val creationResult = doAuthenticatedRequest(POST, "/v1/studies", Some(studyJson("Heavy Light Study")))
        status(creationResult) must equal(201)
        val createdId = contentAsJson(creationResult).apply("id").as[Long]

        val authService = inject[AuthService]
        val george = doSync(authService.register("George Wilson", "iamgeorge"))

        status(doAuthenticatedRequest(PUT, s"/v1/studies/$createdId/participants/${george.id}")) must equal(201)

        val list = contentAsJson(doAuthenticatedRequest(GET, s"/v1/studies/$createdId/participants")).as[JsArray].value
        list.length must equal(1)
        list.head.apply("name").as[String] must equal("George Wilson")

        status(doAuthenticatedRequest(DELETE, s"/v1/studies/$createdId/participants/${george.id}")) must equal(200)

        val finalList = contentAsJson(doAuthenticatedRequest(GET, s"/v1/studies/$createdId/participants")).as[JsArray].value
        finalList.length must equal(0)
      }
    }
  }

  private def studyJson(name: String): JsValue = {
    Json.obj(
      "name" -> name
    )
  }

}
