package v1.answer

import models.Question
import models.Questions
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.JsArray
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test._
import util.Authenticated
import util.FreshDatabase
import util.TestUtils

class AnswerControllerSpec extends PlaySpec
  with GuiceOneAppPerSuite with FreshDatabase with Injecting with TestUtils with Authenticated {

  "AnswerController" when {
    "unauthorized" should {
      "reject requests index requests" in {
        val response = doRequest(GET, "/v1/answers")
        status(response) must equal(UNAUTHORIZED)
      }
    }

    "logged in" should {
      "reject answer with invalid question id" in {
        val response = doAuthenticatedRequest(POST, "/v1/answers", Some(answerJson(999, "Some answer A")))
        status(response) must equal(BAD_REQUEST)
      }

      "accept, then serve and delete answer with valid question id" in {
        val question = doSync(Questions.add(Question(0, "My question B")))

        val response = doAuthenticatedRequest(POST, "/v1/answers", Some(answerJson(question.id, "Some answer B")))
        status(response) must equal(CREATED)

        val newItemJson = contentAsJson(response)
        val newItemId = newItemJson("id").as[Long]
        val readJson = contentAsJson(doAuthenticatedRequest(GET, s"/v1/answers/$newItemId"))
        readJson("id").as[Long] must equal(newItemId)

        status(doAuthenticatedRequest(DELETE, s"/v1/answers/$newItemId")) must equal(OK)
        val finalList = contentAsJson(doAuthenticatedRequest(GET, s"/v1/answers")).as[JsArray].value
        finalList.size must equal(0)

        // delete question again to leave db in initial state
        doSync(Questions.delete(question.id)) must equal(1)
      }
    }
  }

  private def answerJson(questionId: Long, text: String): JsValue = {
    Json.obj(
      "question_id" -> questionId,
      "content" -> text
    )
  }
}
