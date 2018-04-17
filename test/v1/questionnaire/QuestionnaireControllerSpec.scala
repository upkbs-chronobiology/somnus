package v1.questionnaire

import auth.roles.Role
import models.AnswerType
import models.Question
import models.Questionnaire
import models.QuestionnaireRepository
import models.QuestionsRepository
import models.Study
import models.StudyRepository
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.JsArray
import play.api.libs.json.JsNumber
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.Injecting
import testutil.Authenticated
import testutil.FreshDatabase
import testutil.TestUtils

class QuestionnaireControllerSpec extends PlaySpec
  with GuiceOneAppPerSuite with Injecting with FreshDatabase with TestUtils with Authenticated {

  val study = doSync(inject[StudyRepository].create(Study(0, "Sample Study")))
  val questionnaire = doSync(inject[QuestionnaireRepository].create(Questionnaire(0, "Sample Questionnaire", Some(study.id))))
  val question = doSync(inject[QuestionsRepository].add(Question(0, "Sample Question", AnswerType.RangeDiscrete, None, Some("1,5"), Some(questionnaire.id))))

  "QuestionnaireController" when {
    "not logged in" should {
      "reject requests" in {
        status(doRequest(GET, s"/v1/questionnaires")) must equal(UNAUTHORIZED)
        status(doRequest(POST, s"/v1/questionnaires", Some(qJson("Foo bar", None)))) must equal(UNAUTHORIZED)
        status(doRequest(PUT, s"/v1/questionnaires/${questionnaire.id}", Some(qJson("Foo bar", None)))) must equal(UNAUTHORIZED)
        status(doRequest(DELETE, s"/v1/questionnaires/${questionnaire.id}")) must equal(UNAUTHORIZED)
        status(doRequest(GET, s"/v1/questionnaires/${questionnaire.id}/questions")) must equal(UNAUTHORIZED)
      }
    }

    "logged in as base user" should {
      "reject requests" in {
        status(doAuthenticatedRequest(GET, s"/v1/questionnaires")) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(POST, s"/v1/questionnaires", Some(qJson("Foo bar", None)))) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(PUT, s"/v1/questionnaires/${questionnaire.id}", Some(qJson("Foo bar", None)))) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(DELETE, s"/v1/questionnaires/${questionnaire.id}")) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(GET, s"/v1/questionnaires/${questionnaire.id}/questions")) must equal(FORBIDDEN)
      }
    }

    "logged in as researcher" should {
      implicit val _ = Role.Researcher

      "list questionnaires" in {
        val result = doAuthenticatedRequest(GET, s"/v1/questionnaires")

        status(result) must equal(OK)

        val list = contentAsJson(result).as[JsArray].value
        list.length must equal(1)
        list.head("name").as[String] must equal("Sample Questionnaire")
      }

      "list questions" in {
        val response = doAuthenticatedRequest(GET, s"/v1/questionnaires/${questionnaire.id}/questions")

        status(response) must equal(OK)
        val questions = contentAsJson(response).as[JsArray].value
        questions.length must equal(1)
        questions.head("content").as[String] must equal("Sample Question")
      }

      "create, update and delete questionnaires" in {
        val questionnaireRepo = inject[QuestionnaireRepository]

        val postResult = doAuthenticatedRequest(POST, s"/v1/questionnaires", Some(qJson("Bar foo", None)))
        status(postResult) must equal(CREATED)
        val listAfterPost = doSync(questionnaireRepo.listAll())
        listAfterPost.length must equal(2)
        listAfterPost.map(q => q.name) must contain("Bar foo")

        val newId = contentAsJson(postResult).apply("id").as[Long]

        status(doAuthenticatedRequest(PUT, s"/v1/questionnaires/$newId", Some(qJson("Bar foo 2", None)))) must equal(OK)
        val listAfterPut = doSync(questionnaireRepo.listAll())
        listAfterPut.length must equal(2)
        listAfterPut.map(q => q.name) mustNot contain("Bar foo")
        listAfterPut.map(q => q.name) must contain("Bar foo 2")

        status(doAuthenticatedRequest(DELETE, s"/v1/questionnaires/$newId")) must equal(OK)
        val listAfterDelete = doSync(questionnaireRepo.listAll())
        listAfterDelete.length must equal(1)
        listAfterDelete.map(q => q.name) mustNot contain("Bar foo")
        listAfterDelete.map(q => q.name) mustNot contain("Bar foo 2")
      }

      "give error status when trying to delete non-existent questionnaire" in {
        status(doAuthenticatedRequest(DELETE, "/v1/questionnaires/999")) must equal(NOT_FOUND)
      }

      "reject deleting questionnaires containing questions" in {
        status(doAuthenticatedRequest(DELETE, s"/v1/questionnaires/${questionnaire.id}")) must equal(BAD_REQUEST)
      }
    }
  }

  private def qJson(name: String, studyId: Option[Long]): JsValue = {
    val obj = Json.obj(
      "name" -> name
    )
    if (studyId.isEmpty) obj else obj + ("studyId" -> JsNumber(studyId.get))
  }
}
