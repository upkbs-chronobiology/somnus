package v1.answer

import java.time.OffsetDateTime

import scala.concurrent.Future

import auth.roles.Role
import auth.roles.Role.Role
import models.AnswerType
import models.AnswersRepository
import models.Question
import models.QuestionsRepository
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
import testutil.TestUtils

class AnswerControllerSpec extends PlaySpec
  with GuiceOneAppPerSuite with FreshDatabase with Injecting with TestUtils with Authenticated {

  val questionsRepo = inject[QuestionsRepository]
  val answersRepo = inject[AnswersRepository]

  "AnswerController" when {
    "unauthorized" should {
      "reject index requests" in {
        val response = doRequest(GET, "/v1/answers")
        status(response) must equal(UNAUTHORIZED)
      }
    }

    "logged in as base user" should {
      "reject indexing and CRUD requests" in {
        status(doAuthenticatedRequest(GET, "/v1/answers")) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(GET, "/v1/answers/1")) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(DELETE, "/v1/answers/1")) must equal(FORBIDDEN)
      }

      "accept answers" in {
        val questionX = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.Text)))
        val questionY = doSync(questionsRepo.add(Question(0, "My question Y", AnswerType.Text)))

        val data = Json.arr(
          answerJson(questionX.id, "Some answer X", "2018-05-16T14:08:43+02:00"),
          answerJson(questionY.id, "Some answer Y", "2099-01-01T01:01:55-11:30")
        )
        val response = doAuthenticatedRequest(POST, "/v1/answers", Some(data))
        status(response) must equal(CREATED)

        val newAnswers = contentAsJson(response).as[JsArray].value
        newAnswers.size must equal(2)

        val firstNewAnswer = newAnswers(0)
        firstNewAnswer("id").as[Long] must be >= 0L
        firstNewAnswer("questionId").as[Long] must equal(questionX.id)
        firstNewAnswer("content").as[String] must equal("Some answer X")
        firstNewAnswer("createdLocal").as[String] must equal("2018-05-16T14:08:43+02:00")
      }

      "properly deal with zero-truncated timestamps" in {
        val questionX = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.Text)))
        val questionY = doSync(questionsRepo.add(Question(0, "My question Y", AnswerType.Text)))

        val data = Json.arr(
          answerJson(questionX.id, "Some answer X", "2019-09-21T13:14:15.98+02:00"),
          answerJson(questionY.id, "Some answer Y", "2019-09-21T13:14:15.9876+02:00")
        )
        val response = doAuthenticatedRequest(POST, "/v1/answers", Some(data))
        status(response) must equal(CREATED)

        val newAnswers = contentAsJson(response).as[JsArray].value
        val firstNewAnswer = newAnswers(0)
        firstNewAnswer("createdLocal").as[String] must equal("2019-09-21T13:14:15.980+02:00")
        val secondNewAnswer = newAnswers(1)
        secondNewAnswer("createdLocal").as[String] must equal("2019-09-21T13:14:15.987600+02:00")
      }
    }

    "logged in as researcher" should {
      implicit val _ = Role.Researcher

      "reject single answer with invalid question id" in {
        val response = doAuthenticatedRequest(POST, "/v1/answers",
          Some(Json.arr(answerJson(999, "Some answer A", "2000-01-01T00:00:00+00:00"))))
        status(response) must equal(BAD_REQUEST)
      }

      "reject multiple answers with one invalid question id" in {
        val question = doSync(questionsRepo.add(Question(0, "My question B", AnswerType.Text)))

        val data = Json.arr(
          answerJson(question.id, "I will not be created", "2000-01-01T00:00:00+00:00"),
          answerJson(999, "Some answer A", "2000-01-01T00:00:00+00:00")
        )
        val response = doAuthenticatedRequest(POST, "/v1/answers", Some(data))
        status(response) must equal(BAD_REQUEST)

        doSync(answersRepo.listAll()).map(_.content) must not contain "I will not be created"

        doSync(questionsRepo.delete(question.id)) must equal(1)
      }

      "accept, then serve and delete answers with valid question ids" in {
        implicit val _ = Role.Researcher

        val initialList = contentAsJson(doAuthenticatedRequest(GET, s"/v1/answers")).as[JsArray].value

        val questionX = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.Text)))
        val questionY = doSync(questionsRepo.add(Question(0, "My question Y", AnswerType.Text)))

        val data = Json.arr(
          answerJson(questionX.id, "Some answer X", "2000-01-01T00:00:00+00:00"),
          answerJson(questionY.id, "Some answer Y", "2000-01-01T00:00:00+00:00")
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
        finalList.size - initialList.size must equal(0)

        // delete question again to leave db in initial state
        doSync(questionsRepo.delete(questionX.id)) must equal(1)
        doSync(questionsRepo.delete(questionY.id)) must equal(1)
      }

      "accept discrete-number answers" in {
        val question = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.RangeDiscrete, None, Some("1,5"))))
        status(postAnswer(question.id, "4")) must equal(201)
      }

      "accept continuous-number answers" in {
        val question = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.RangeContinuous, None, Some("0,1"))))
        status(postAnswer(question.id, "0.1337")) must equal(201)
      }

      "accept continuous-number answers within custom range" in {
        val question = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.RangeContinuous, answerRange = Some("-2,5"))))
        status(postAnswer(question.id, "4.44")) must equal(201)
        status(postAnswer(question.id, "-0.1")) must equal(201)
      }

      "accept single-select multiple-choice answers" in {
        val question = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.MultipleChoiceSingle, Some("a,b,c"))))
        status(postAnswer(question.id, "0")) must equal(CREATED)
        status(postAnswer(question.id, "2")) must equal(CREATED)
      }

      "accept many-select multiple-choice answers" in {
        val question = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.MultipleChoiceMany, Some("a,b,c"))))
        status(postAnswer(question.id, "0")) must equal(CREATED)
        status(postAnswer(question.id, "2")) must equal(CREATED)
        status(postAnswer(question.id, "0,1,2")) must equal(CREATED)
      }

      "reject text answers to continuous-number type questions" in {
        val question = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.RangeContinuous)))
        status(postAnswer(question.id, "This should not be text")) must equal(400)
      }

      "reject text answers to discrete-number type questions" in {
        val question = doSync(questionsRepo.add(Question(0, "My question Y", AnswerType.RangeDiscrete, None, Some("1,5"))))
        status(postAnswer(question.id, "This should not be text")) must equal(400)
      }

      "reject continuous-number type answers out of range" in {
        val question = doSync(questionsRepo.add(Question(0, "My question Z", AnswerType.RangeContinuous)))
        status(postAnswer(question.id, "1.5")) must equal(400)
      }

      "reject continuous-number type answers out of custom range" in {
        val question = doSync(questionsRepo.add(Question(0, "My question Z", AnswerType.RangeContinuous, answerRange = Some("13,177"))))
        status(postAnswer(question.id, "0.5")) must equal(400)
      }

      "reject discrete-number type answers out of range" in {
        val question = doSync(questionsRepo.add(Question(0, "My question W", AnswerType.RangeDiscrete, None, Some("2,6"))))
        status(postAnswer(question.id, "7")) must equal(400)
      }

      "reject continuous number answers to discrete-number type questions" in {
        val question = doSync(questionsRepo.add(Question(0, "My question Z", AnswerType.RangeDiscrete, None, Some("1,5"))))
        status(postAnswer(question.id, "2.5")) must equal(400)
      }

      "reject single-select multiple-choice answers out of range" in {
        val question = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.MultipleChoiceSingle, Some("a,b,c"))))
        status(postAnswer(question.id, "-1")) must equal(BAD_REQUEST)
        status(postAnswer(question.id, "3")) must equal(BAD_REQUEST)
      }

      "reject many-select multiple-choice answers out of range" in {
        val question = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.MultipleChoiceMany, Some("a,b,c"))))
        status(postAnswer(question.id, "-1")) must equal(BAD_REQUEST)
        status(postAnswer(question.id, "3")) must equal(BAD_REQUEST)
        status(postAnswer(question.id, "-1,2")) must equal(BAD_REQUEST)
        status(postAnswer(question.id, "2,4,3")) must equal(BAD_REQUEST)
      }

      "reject single-select multiple-choice answers other than integers" in {
        val question = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.MultipleChoiceSingle, Some("a,b,c"))))
        status(postAnswer(question.id, "1.2")) must equal(BAD_REQUEST)
        status(postAnswer(question.id, "foobar")) must equal(BAD_REQUEST)
      }

      "reject many-select multiple-choice answers other than integers" in {
        val question = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.MultipleChoiceMany, Some("a,b,c"))))
        status(postAnswer(question.id, "1.2")) must equal(BAD_REQUEST)
        status(postAnswer(question.id, "foobar")) must equal(BAD_REQUEST)
        status(postAnswer(question.id, "0,1.2")) must equal(BAD_REQUEST)
        status(postAnswer(question.id, "foobar,1")) must equal(BAD_REQUEST)
      }
    }
  }

  def postAnswer(questionId: Long, content: String)(implicit role: Role): Future[Result] = {
    val payload = Json.arr(answerJson(questionId, content, OffsetDateTime.now().toString))
    doAuthenticatedRequest(POST, "/v1/answers", Some(payload))
  }

  private def answerJson(questionId: Long, text: String, createdLocal: String): JsValue = {
    Json.obj(
      "questionId" -> questionId,
      "content" -> text,
      "createdLocal" -> createdLocal
    )
  }
}
