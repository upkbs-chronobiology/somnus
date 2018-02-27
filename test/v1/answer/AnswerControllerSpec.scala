package v1.answer

import models.Answers
import models.Question
import models.Questions
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.JsArray
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test._
import testutil.Authenticated
import testutil.FreshDatabase
import testutil.TestUtils
import testutil.FreshDatabase
import testutil.TestUtils

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
      "reject single answer with invalid question id" in {
        val response = doAuthenticatedRequest(POST, "/v1/answers", Some(Json.arr(answerJson(999, "Some answer A"))))
        status(response) must equal(BAD_REQUEST)
      }

      "reject multiple answers with one invalid question id" in {
        val question = doSync(Questions.add(Question(0, "My question B")))

        val data = Json.arr(
          answerJson(question.id, "I will not be created"),
          answerJson(999, "Some answer A")
        )
        val response = doAuthenticatedRequest(POST, "/v1/answers", Some(data))
        status(response) must equal(BAD_REQUEST)

        doSync(Answers.listAll()).map(_.content) must not contain "I will not be created"

        doSync(Questions.delete(question.id)) must equal(1)
      }

      "accept, then serve and delete answers with valid question ids" in {
        val questionX = doSync(Questions.add(Question(0, "My question X")))
        val questionY = doSync(Questions.add(Question(0, "My question Y")))

        val data = Json.arr(
          answerJson(questionX.id, "Some answer X"),
          answerJson(questionY.id, "Some answer Y")
        )
        val response = doAuthenticatedRequest(POST, "/v1/answers", Some(data))
        status(response) must equal(CREATED)

        contentAsJson(response).as[JsArray].value.foreach { newItemJson =>
          val newItemId = newItemJson("id").as[Long]
          val readJson = contentAsJson(doAuthenticatedRequest(GET, s"/v1/answers/$newItemId"))
          readJson("id").as[Long] must equal(newItemId)

          status(doAuthenticatedRequest(DELETE, s"/v1/answers/$newItemId")) must equal(OK)
        }

        val finalList = contentAsJson(doAuthenticatedRequest(GET, s"/v1/answers")).as[JsArray].value
        finalList.size must equal(0)

        // delete question again to leave db in initial state
        doSync(Questions.delete(questionX.id)) must equal(1)
        doSync(Questions.delete(questionY.id)) must equal(1)
      }
    }
  }

  private def answerJson(questionId: Long, text: String): JsValue = {
    Json.obj(
      "questionId" -> questionId,
      "content" -> text
    )
  }
}
