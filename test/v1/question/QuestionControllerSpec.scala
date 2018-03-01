package v1.question

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import auth.roles.Role
import models.AnswerType
import models.AnswerType.AnswerType
import models.Questions
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.JsArray
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test._
import testutil.Authenticated
import testutil.FreshDatabase

class QuestionControllerSpec
  extends PlaySpec with GuiceOneAppPerSuite with FreshDatabase with Injecting with Authenticated {

  "QuestionController index" should {
    "reject unauthorized users" in {
      val result = doRequest(GET, "/v1/questions")
      status(result) must equal(UNAUTHORIZED)
    }

    "initially serve an empty JSON array" in {
      val request = AuthenticatedFakeRequest(GET, "/v1/questions")
      val list = route(app, request).get

      contentAsString(list) must equal("[]")
    }
  }

  "QuestionController" should {
    "refuse adding or deleting question to basic users" in {
      val resultCreation = doAuthenticatedRequest(POST, "/v1/questions", Some(questionJson("This won't get through?")))
      status(resultCreation) must equal(FORBIDDEN)

      val resultDeletion = doAuthenticatedRequest(DELETE, "/v1/questions/321")
      status(resultDeletion) must equal(FORBIDDEN)

      doSync(Questions.listAll.map(_.size)) must equal(0)
    }

    // XXX: Split into multiple tests? Would order be guaranteed?
    "create, serve and delete questions" in {
      status(postQuestion("Huh A?")) must equal(201)
      val secondQuestionResult = postQuestion("Huh B?")
      status(secondQuestionResult) must equal(201)
      status(postQuestion("Huh C?")) must equal(201)

      val secondId = (contentAsJson(secondQuestionResult) \ "id").result.as[Long]
      val getRequest = AuthenticatedFakeRequest(GET, s"/v1/questions/$secondId")
      val getResult = route(app, getRequest).get
      status(getResult) must equal(200)

      val foundObj = contentAsJson(getResult)
      foundObj("id").as[Long] must equal(secondId)
      foundObj("content").as[String] must equal("Huh B?")

      val listResult = route(app, AuthenticatedFakeRequest(GET, "/v1/questions")).get
      status(listResult) must equal(200)

      val foundList = contentAsJson(listResult).as[JsArray].value
      foundList.size must equal(3)
      foundList.map(_ ("id")).map(_.as[Long]) must equal(Seq(secondId - 1, secondId, secondId + 1))
      foundList.map(_ ("content").as[String]) must equal(Seq("Huh A?", "Huh B?", "Huh C?"))

      status(deleteQuestion(secondId - 1)) must equal(200)
      status(deleteQuestion(secondId)) must equal(200)

      val secondListResult = route(app, AuthenticatedFakeRequest(GET, "/v1/questions")).get
      val secondList = contentAsJson(secondListResult).as[JsArray].value
      secondList.size must equal(1)
      secondList.head("id").as[Long] must equal(secondId + 1)

      status(deleteQuestion(secondId + 1)) must equal(200)
      val finalListResult = route(app, AuthenticatedFakeRequest(GET, "/v1/questions")).get
      val finalList = contentAsJson(finalListResult).as[JsArray].value
      finalList.size must equal(0)
    }

    "reject non-editor updates" in {
      val questionResult = postQuestion("Before?")
      status(questionResult) must equal(201)
      val id = contentAsJson(questionResult).apply("id").as[Long]

      val putResponse = doAuthenticatedRequest(PUT, s"/v1/questions/$id", Some(questionJson("After?")), role = None)
      status(putResponse) must equal(403)
    }

    "update existing questions" in {
      val questionResult = postQuestion("Before?")
      status(questionResult) must equal(201)
      val id = contentAsJson(questionResult).apply("id").as[Long]

      val jsonBefore = contentAsJson(doAuthenticatedRequest(GET, s"/v1/questions/$id"))
      jsonBefore("content").as[String] must equal("Before?")

      val putResponse = doAuthenticatedRequest(PUT, s"/v1/questions/$id", Some(questionJson("After?")), role = Some(Role.Researcher))
      status(putResponse) must equal(200)

      val jsonAfter = contentAsJson(doAuthenticatedRequest(GET, s"/v1/questions/$id"))
      jsonAfter("content").as[String] must equal("After?")
    }

    "refuse questions with missing answer type" in {
      val payload = Json.obj("content" -> "foo bar baz?")
      val request = AuthenticatedFakeRequest(Role.Researcher)(POST, "/v1/questions").withBody(payload)
      status(route(app, request).get) must equal(400)
    }

    "refuse questions with invalid answer type" in {
      val request = doAuthenticatedRequest(POST, "/v1/questions",
        Some(questionJson("Nanana batman?", "non-existent-type")), role = Some(Role.Researcher))
      println(contentAsString(request))
      status(request) must equal(400)
    }

    "accept questions of all possible answer types" in {
      status(postQuestion("A?", AnswerType.Text)) must equal(201)
      status(postQuestion("B?", AnswerType.RangeContinuous)) must equal(201)
      status(postQuestion("C?", AnswerType.RangeDiscrete5)) must equal(201)
    }
  }

  private def postQuestion(text: String, answerType: AnswerType = AnswerType.Text): Future[Result] = {
    val postRequest = AuthenticatedFakeRequest(Role.Researcher)(POST, "/v1/questions")
      .withBody(questionJson(text, answerType.toString))
    route(app, postRequest).get
  }

  private def deleteQuestion(id: Long) = {
    val deleteRequest = AuthenticatedFakeRequest(Role.Researcher)(DELETE, s"/v1/questions/$id")
    route(app, deleteRequest).get
  }

  private def questionJson(text: String, answerType: String = AnswerType.Text.toString): JsValue = {
    Json.obj(
      "content" -> text,
      "answerType" -> answerType
    )
  }
}
