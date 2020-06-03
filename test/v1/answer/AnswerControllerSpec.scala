package v1.answer

import java.time.OffsetDateTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import auth.roles.Role
import models.AccessLevel
import models.Answer
import models.AnswersRepository
import models.AnswerType
import models.Question
import models.Questionnaire
import models.QuestionnairesRepository
import models.QuestionsRepository
import models.Study
import models.StudyAccess
import models.StudyAccessRepository
import models.StudyRepository
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.mvc.Result
import play.api.test._
import play.api.test.Helpers._
import testutil.Authenticated
import testutil.FreshDatabase
import testutil.TestUtils
import util.Futures.TraversableFutureExtensions

class AnswerControllerSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with FreshDatabase
    with Injecting
    with TestUtils
    with Authenticated
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  val questionsRepo = inject[QuestionsRepository]
  val answersRepo = inject[AnswersRepository]

  // TODO: Somewhat boilerplatey - extract to trait?
  private lazy val hiddenStudy = doSync(inject[StudyRepository].create(Study(0, "Hidden Study")))
  private lazy val readableStudy = doSync(inject[StudyRepository].create(Study(0, "Readable Study")))

  private lazy val hiddenQuestionnaire =
    doSync(inject[QuestionnairesRepository].create(Questionnaire(0, "Hidden Questionnaire", Some(hiddenStudy.id))))
  private lazy val readableQuestionnaire =
    doSync(inject[QuestionnairesRepository].create(Questionnaire(0, "Readable Questionnaire", Some(readableStudy.id))))

  private lazy val hiddenQuestion = doSync(
    questionsRepo.add(Question(0, "hidden question", AnswerType.Text, questionnaireId = Some(hiddenQuestionnaire.id)))
  )
  private lazy val readableQuestion = doSync(
    questionsRepo
      .add(Question(0, "readable question", AnswerType.Text, questionnaireId = Some(readableQuestionnaire.id)))
  )

  override def beforeAll(): Unit = {
    super.beforeAll()

    doSync(inject[StudyAccessRepository].upsert(StudyAccess(researchUser.id, readableStudy.id, AccessLevel.Read)))
  }

  override def afterEach(): Unit = {
    super.afterEach()

    // delete all answers
    doSync(answersRepo.listAll().mapTraversableAsync(a => answersRepo.delete(a.id)))
  }

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

      "reject single answer with invalid question id" in {
        val response = doAuthenticatedRequest(
          POST,
          "/v1/answers",
          Some(Json.arr(answerJson(999, "Some answer A", "2000-01-01T00:00:00+00:00")))
        )
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

      "accept discrete-number answers" in {
        val question =
          doSync(questionsRepo.add(Question(0, "My question X", AnswerType.RangeDiscrete, None, Some("1,5"))))
        status(postAnswer(question.id, "4")) must equal(201)
      }

      "accept continuous-number answers" in {
        val question =
          doSync(questionsRepo.add(Question(0, "My question X", AnswerType.RangeContinuous, None, Some("0,1"))))
        status(postAnswer(question.id, "0.1337")) must equal(201)
      }

      "accept continuous-number answers within custom range" in {
        val question = doSync(
          questionsRepo.add(Question(0, "My question X", AnswerType.RangeContinuous, answerRange = Some("-2,5")))
        )
        status(postAnswer(question.id, "4.44")) must equal(201)
        status(postAnswer(question.id, "-0.1")) must equal(201)
      }

      "accept single-select multiple-choice answers" in {
        val question =
          doSync(questionsRepo.add(Question(0, "My question X", AnswerType.MultipleChoiceSingle, Some("a,b,c"))))
        status(postAnswer(question.id, "0")) must equal(CREATED)
        status(postAnswer(question.id, "2")) must equal(CREATED)
      }

      "accept many-select multiple-choice answers" in {
        val question =
          doSync(questionsRepo.add(Question(0, "My question X", AnswerType.MultipleChoiceMany, Some("a,b,c"))))
        status(postAnswer(question.id, "0")) must equal(CREATED)
        status(postAnswer(question.id, "2")) must equal(CREATED)
        status(postAnswer(question.id, "0,1,2")) must equal(CREATED)
      }

      "accept time answers" in {
        val question = doSync(questionsRepo.add(Question(0, "When did humans appear?", AnswerType.TimeOfDay)))
        status(postAnswer(question.id, "23:55:04")) must equal(201)
      }

      "accept date answers" in {
        val question = doSync(questionsRepo.add(Question(0, "What is your birthday?", AnswerType.Date)))
        status(postAnswer(question.id, "2020-02-29")) must equal(201)
      }

      "reject text answers to continuous-number type questions" in {
        val question = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.RangeContinuous)))
        status(postAnswer(question.id, "This should not be text")) must equal(400)
      }

      "reject text answers to discrete-number type questions" in {
        val question =
          doSync(questionsRepo.add(Question(0, "My question Y", AnswerType.RangeDiscrete, None, Some("1,5"))))
        status(postAnswer(question.id, "This should not be text")) must equal(400)
      }

      "reject continuous-number type answers out of range" in {
        val question = doSync(questionsRepo.add(Question(0, "My question Z", AnswerType.RangeContinuous)))
        status(postAnswer(question.id, "1.5")) must equal(400)
      }

      "reject continuous-number type answers out of custom range" in {
        val question = doSync(
          questionsRepo.add(Question(0, "My question Z", AnswerType.RangeContinuous, answerRange = Some("13,177")))
        )
        status(postAnswer(question.id, "0.5")) must equal(400)
      }

      "reject discrete-number type answers out of range" in {
        val question =
          doSync(questionsRepo.add(Question(0, "My question W", AnswerType.RangeDiscrete, None, Some("2,6"))))
        status(postAnswer(question.id, "7")) must equal(400)
      }

      "reject continuous number answers to discrete-number type questions" in {
        val question =
          doSync(questionsRepo.add(Question(0, "My question Z", AnswerType.RangeDiscrete, None, Some("1,5"))))
        status(postAnswer(question.id, "2.5")) must equal(400)
      }

      "reject single-select multiple-choice answers out of range" in {
        val question =
          doSync(questionsRepo.add(Question(0, "My question X", AnswerType.MultipleChoiceSingle, Some("a,b,c"))))
        status(postAnswer(question.id, "-1")) must equal(BAD_REQUEST)
        status(postAnswer(question.id, "3")) must equal(BAD_REQUEST)
      }

      "reject many-select multiple-choice answers out of range" in {
        val question =
          doSync(questionsRepo.add(Question(0, "My question X", AnswerType.MultipleChoiceMany, Some("a,b,c"))))
        status(postAnswer(question.id, "-1")) must equal(BAD_REQUEST)
        status(postAnswer(question.id, "3")) must equal(BAD_REQUEST)
        status(postAnswer(question.id, "-1,2")) must equal(BAD_REQUEST)
        status(postAnswer(question.id, "2,4,3")) must equal(BAD_REQUEST)
      }

      "reject single-select multiple-choice answers other than integers" in {
        val question =
          doSync(questionsRepo.add(Question(0, "My question X", AnswerType.MultipleChoiceSingle, Some("a,b,c"))))
        status(postAnswer(question.id, "1.2")) must equal(BAD_REQUEST)
        status(postAnswer(question.id, "foobar")) must equal(BAD_REQUEST)
      }

      "reject many-select multiple-choice answers other than integers" in {
        val question =
          doSync(questionsRepo.add(Question(0, "My question X", AnswerType.MultipleChoiceMany, Some("a,b,c"))))
        status(postAnswer(question.id, "1.2")) must equal(BAD_REQUEST)
        status(postAnswer(question.id, "foobar")) must equal(BAD_REQUEST)
        status(postAnswer(question.id, "0,1.2")) must equal(BAD_REQUEST)
        status(postAnswer(question.id, "foobar,1")) must equal(BAD_REQUEST)
      }

      "reject invalid time answers" in {
        val question = doSync(questionsRepo.add(Question(0, "Question about time", AnswerType.TimeOfDay)))
        status(postAnswer(question.id, "Once upon a time")) must equal(400)
      }

      "reject invalid date answers" in {
        val question = doSync(questionsRepo.add(Question(0, "Question about date", AnswerType.Date)))
        status(postAnswer(question.id, "The day after tomorrow")) must equal(400)
      }

      "accept answers to studies where participant" in {
        val study = doSync(inject[StudyRepository].create(Study(0, "My Study")))
        doSync(inject[StudyRepository].addParticipant(study.id, baseUser.id))
        val questionnaire =
          doSync(inject[QuestionnairesRepository].create(Questionnaire(0, "My Questionnaire", Some(study.id))))
        val question = doSync(
          questionsRepo.add(Question(0, "My Question", AnswerType.Text, questionnaireId = Some(questionnaire.id)))
        )

        val data = Json.arr(answerJson(question.id, "Some answer", "2099-01-01T01:01:55-11:30"))
        val response = doAuthenticatedRequest(POST, "/v1/answers", Some(data))
        status(response) must equal(CREATED)
      }

      "reject answers to studies where not participant" in {
        val study = doSync(inject[StudyRepository].create(Study(0, "Unrelated Study")))
        val questionnaire =
          doSync(inject[QuestionnairesRepository].create(Questionnaire(0, "Unrelated Questionnaire", Some(study.id))))
        val question = doSync(
          questionsRepo
            .add(Question(0, "Unrelated Question", AnswerType.Text, questionnaireId = Some(questionnaire.id)))
        )

        val data = Json.arr(answerJson(question.id, "Some answer", "2099-01-01T01:01:55-11:30"))
        val response = doAuthenticatedRequest(POST, "/v1/answers", Some(data))
        status(response) must equal(FORBIDDEN)
      }

      "not save any answers if one was rejected" in {
        val study = doSync(inject[StudyRepository].create(Study(0, "Unrelated Study 2")))
        val questionnaire =
          doSync(inject[QuestionnairesRepository].create(Questionnaire(0, "Unrelated Questionnaire", Some(study.id))))
        val hiddenQuestion = doSync(
          questionsRepo.add(Question(0, "Hidden Question", AnswerType.Text, questionnaireId = Some(questionnaire.id)))
        )
        val visibleQuestion = doSync(
          questionsRepo.add(Question(0, "Visible Question", AnswerType.Text, questionnaireId = Some(questionnaire.id)))
        )

        val data = Json.arr(
          answerJson(visibleQuestion.id, "Some answer", "2099-01-01T01:01:55-11:30"),
          answerJson(hiddenQuestion.id, "Some answer", "2099-01-01T01:01:55-11:30")
        )
        val response = doAuthenticatedRequest(POST, "/v1/answers", Some(data))

        status(response) must equal(FORBIDDEN)
        val answerQuestionIds = answersRepo.listAll().mapTraversable(_.questionId)
        doSync(answerQuestionIds) must contain noneOf (hiddenQuestion.id, visibleQuestion.id)
      }
    }

    "logged in as researcher" should {
      implicit val _ = Role.Researcher

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

      "list answers on readable studies" in {
        val answer = doSync(
          answersRepo.add(Answer(0, readableQuestion.id, "Some answer", baseUser.id, null, OffsetDateTime.now()))
        )

        val list = contentAsJson(doAuthenticatedRequest(GET, "/v1/answers")).as[JsArray].value
        list.map(_("id").as[Long]) must contain(answer.id)
      }

      "omit answers on hidden studies from list" in {
        val answer =
          doSync(answersRepo.add(Answer(0, hiddenQuestion.id, "Some answer", baseUser.id, null, OffsetDateTime.now())))

        val list = contentAsJson(doAuthenticatedRequest(GET, "/v1/answers")).as[JsArray].value
        list.map(_("id").as[Long]) must not contain answer.id
      }

      "allow reading answers on readable studies" in {
        val answer = doSync(
          answersRepo.add(Answer(0, readableQuestion.id, "Some answer", baseUser.id, null, OffsetDateTime.now()))
        )

        val response = doAuthenticatedRequest(GET, s"/v1/answers/${answer.id}")
        status(response) must equal(OK)
      }

      "reject reading hidden studies" in {
        val answer =
          doSync(answersRepo.add(Answer(0, hiddenQuestion.id, "Some answer", baseUser.id, null, OffsetDateTime.now())))

        val response = doAuthenticatedRequest(GET, s"/v1/answers/${answer.id}")
        status(response) must equal(FORBIDDEN)
      }
    }
  }

  def postAnswer(questionId: Long, content: String): Future[Result] = {
    val payload = Json.arr(answerJson(questionId, content, OffsetDateTime.now().toString))
    doAuthenticatedRequest(POST, "/v1/answers", Some(payload))
  }

  private def answerJson(questionId: Long, text: String, createdLocal: String): JsValue = {
    Json.obj("questionId" -> questionId, "content" -> text, "createdLocal" -> createdLocal)
  }
}
