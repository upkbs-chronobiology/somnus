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
import play.api.test.Helpers._
import play.api.test.Injecting
import testutil.Authenticated
import testutil.FreshDatabase
import testutil.TestUtils

class QuestionnaireControllerSpec extends PlaySpec
  with GuiceOneAppPerSuite with Injecting with FreshDatabase with TestUtils with Authenticated {

  val study = doSync(inject[StudyRepository].create(Study(0, "Sample Study")))
  val questionnaire = doSync(inject[QuestionnaireRepository].create(Questionnaire(0, "Sample Questionnaire", Some(study.id))))
  val question = doSync(inject[QuestionsRepository].add(Question(0, "Sample Question", AnswerType.RangeDiscrete5, Some(questionnaire.id))))

  "QuestionnaireController" when {
    "not logged in" should {
      "reject requests" in {
        status(doRequest(GET, s"/v1/questionnaires/${questionnaire.id}/questions")) must equal(UNAUTHORIZED)
      }
    }

    "logged in as base user" should {
      "reject requests" in {
        status(doAuthenticatedRequest(GET, s"/v1/questionnaires/${questionnaire.id}/questions")) must equal(FORBIDDEN)
      }
    }

    "logged in as researcher" should {
      "list questions" in {
        val response = doAuthenticatedRequest(GET, s"/v1/questionnaires/${questionnaire.id}/questions", role = Some(Role.Researcher))

        status(response) must equal(OK)
        val questions = contentAsJson(response).as[JsArray].value
        questions.length must equal(1)
        questions.head("content").as[String] must equal("Sample Question")
      }
    }
  }
}
