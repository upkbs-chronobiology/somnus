package v1.question

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Future


class QuestionControllerSpec extends PlaySpec with GuiceOneAppPerSuite with Injecting {

  "QuestionController index" should {
    "initially serve an empty JSON array" in {
      val request = FakeRequest(GET, "/v1/questions")
      val list = route(app, request).get

      contentAsString(list) must equal("[]")
    }
  }

  "QuestionController" should {
    // XXX: Split into multiple tests? Would order be guaranteed?
    "create, serve and delete questions" in {
      status(postQuestion("Huh A?")) must equal(201)
      status(postQuestion("Huh B?")) must equal(201)
      status(postQuestion("Huh C?")) must equal(201)

      val getRequest = FakeRequest(GET, "/v1/questions/2")
      val getResult = route(app, getRequest).get
      status(getResult) must equal(200)

      val foundObj = contentAsJson(getResult)
      foundObj("id").as[Long] must equal(2)
      foundObj("content").as[String] must equal("Huh B?")

      val listResult = route(app, FakeRequest(GET, "/v1/questions")).get
      status(listResult) must equal(200)

      val foundList = contentAsJson(listResult).as[JsArray].value
      foundList.size must equal(3)
      foundList.map(_ ("id")).map(_.as[Long]) must equal(Seq(1, 2, 3))
      foundList.map(_ ("content").as[String]) must equal(Seq("Huh A?", "Huh B?", "Huh C?"))

      status(deleteQuestion(1)) must equal(200)
      status(deleteQuestion(2)) must equal(200)

      val secondListResult = route(app, FakeRequest(GET, "/v1/questions")).get
      val secondList = contentAsJson(secondListResult).as[JsArray].value
      secondList.size must equal(1)
      secondList.head("id").as[Long] must equal(3)

      status(deleteQuestion(3)) must equal(200)
      val finalListResult = route(app, FakeRequest(GET, "/v1/questions")).get
      val finalList = contentAsJson(finalListResult).as[JsArray].value
      finalList.size must equal(0)
    }
  }

  private def postQuestion(text: String): Future[Result] = {
    val postRequest = FakeRequest(POST, "/v1/questions")
      .withBody(questionJson(text))
    route(app, postRequest).get
  }

  private def deleteQuestion(id: Long) = {
    val deleteRequest = FakeRequest(DELETE, s"/v1/questions/$id")
    route(app, deleteRequest).get
  }

  private def questionJson(text: String): JsValue = {
    Json.obj(
      "content" -> text
    )
  }
}
