package v1.question

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import auth.roles.Role
import models.AccessLevel
import models.AnswerType
import models.AnswerType.AnswerType
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
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.mvc.Result
import play.api.test._
import play.api.test.Helpers._
import testutil.Authenticated
import testutil.FreshDatabase
import util.Futures.TraversableFutureExtensions
import util.InclusiveRange
import util.Serialization

class QuestionControllerSpec
  extends PlaySpec with GuiceOneAppPerSuite with FreshDatabase with Injecting with Authenticated
    with BeforeAndAfterAll with BeforeAndAfterEach {

  private val hiddenStudy = doSync(inject[StudyRepository].create(Study(0, "Hidden Study")))
  private val readableStudy = doSync(inject[StudyRepository].create(Study(0, "Readable Study")))

  private val hiddenQuestionnaire =
    doSync(inject[QuestionnairesRepository].create(Questionnaire(0, "Hidden Questionnaire", Some(hiddenStudy.id))))
  private val readableQuestionnaire =
    doSync(inject[QuestionnairesRepository].create(Questionnaire(0, "Readable Questionnaire", Some(readableStudy.id))))

  val questionsRepo = inject[QuestionsRepository]

  override def beforeAll(): Unit = {
    super.beforeAll()

    doSync(inject[StudyAccessRepository].upsert(StudyAccess(researchUser.id, readableStudy.id, AccessLevel.Read)))
  }

  override def afterEach(): Unit = {
    super.afterEach()

    // delete all questions
    doSync(questionsRepo.listAll.mapTraversableAsync(q => questionsRepo.delete(q.id)))
  }

  "QuestionController index" should {
    "reject unauthorized users" in {
      val result = doRequest(GET, "/v1/questions")
      status(result) must equal(UNAUTHORIZED)
    }

    "reject base users" in {
      val result = doAuthenticatedRequest(GET, "/v1/questions")
      status(result) must equal(FORBIDDEN)
    }

    "initially serve an empty JSON array" in {
      val list = doAuthenticatedRequest(GET, "/v1/questions", role = Some(Role.Researcher))

      contentAsString(list) must equal("[]")
    }
  }

  "QuestionController" when {
    "logged in as basic user" should {
      "refuse adding or deleting question" in {
        val resultCreation = doAuthenticatedRequest(POST, "/v1/questions", Some(questionJson("This won't get through?")))
        status(resultCreation) must equal(FORBIDDEN)

        val resultDeletion = doAuthenticatedRequest(DELETE, "/v1/questions/321")
        status(resultDeletion) must equal(FORBIDDEN)

        doSync(questionsRepo.listAll.map(_.size)) must equal(0)
      }

      "reject updates" in {
        val question = doSync(createQuestion("Before?"))
        val putResponse = doAuthenticatedRequest(PUT, s"/v1/questions/${question.id}", Some(questionJson("After?")), role = None)
        status(putResponse) must equal(FORBIDDEN)
      }
    }

    "logged in as researcher" should {
      implicit val _ = Role.Researcher

      // XXX: Split into multiple tests? Would order be guaranteed?
      "create, serve and delete questions" in {
        status(postQuestion("Huh A?")) must equal(201)
        val secondQuestionResult = postQuestion("Huh B?")
        status(secondQuestionResult) must equal(201)
        status(postQuestion("Huh C?")) must equal(201)

        val secondId = (contentAsJson(secondQuestionResult) \ "id").result.as[Long]
        val getResult = doAuthenticatedRequest(GET, s"/v1/questions/$secondId")
        status(getResult) must equal(200)

        val foundObj = contentAsJson(getResult)
        foundObj("id").as[Long] must equal(secondId)
        foundObj("content").as[String] must equal("Huh B?")

        val listResult = doAuthenticatedRequest(GET, "/v1/questions")
        status(listResult) must equal(200)

        val foundList = contentAsJson(listResult).as[JsArray].value
        foundList.size must equal(3)
        foundList.map(_ ("id")).map(_.as[Long]) must equal(Seq(secondId - 1, secondId, secondId + 1))
        foundList.map(_ ("content").as[String]) must equal(Seq("Huh A?", "Huh B?", "Huh C?"))

        status(deleteQuestion(secondId - 1)) must equal(200)
        status(deleteQuestion(secondId)) must equal(200)

        val secondListResult = doAuthenticatedRequest(GET, "/v1/questions")
        val secondList = contentAsJson(secondListResult).as[JsArray].value
        secondList.size must equal(1)
        secondList.head("id").as[Long] must equal(secondId + 1)

        status(deleteQuestion(secondId + 1)) must equal(200)
        val finalListResult = doAuthenticatedRequest(GET, "/v1/questions")
        val finalList = contentAsJson(finalListResult).as[JsArray].value
        finalList.size must equal(0)
      }

      "reject questions with non-existent questionnaire id" in {
        val response = doAuthenticatedRequest(POST, "/v1/questions", Some(questionJson("Foo bar?", questionnaireId = Some(666))))
        status(response) must equal(FORBIDDEN)
      }

      "update existing questions" in {
        val questionnaire = doSync(inject[QuestionnairesRepository].create(Questionnaire(0, "Test Questionnaire", None)))

        val questionResult = postQuestion("Before?")
        status(questionResult) must equal(201)
        val id = contentAsJson(questionResult).apply("id").as[Long]

        val jsonBefore = contentAsJson(doAuthenticatedRequest(GET, s"/v1/questions/$id"))
        jsonBefore("content").as[String] must equal("Before?")

        val putResponse = doAuthenticatedRequest(PUT, s"/v1/questions/$id", Some(questionJson("After?", questionnaireId = Some(questionnaire.id))), role = Some(Role.Researcher))
        status(putResponse) must equal(200)
        contentAsJson(putResponse).apply("content").as[String] must equal("After?")
        contentAsJson(putResponse).apply("questionnaireId").as[Long] must equal(questionnaire.id)

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
        status(request) must equal(400)
      }

      "refuse discrete-range questions missing range" in {
        val questionResult = postQuestion("Test?", AnswerType.RangeDiscrete)
        status(questionResult) must equal(BAD_REQUEST)
      }

      "refuse discrete-range questions with wrong number of labels" in {
        val questionResult = postQuestion("Test?", AnswerType.RangeDiscrete, Some(Seq("a", "b", "c")), Some(InclusiveRange(1, 10)))
        status(questionResult) must equal(BAD_REQUEST)
      }

      "accept questions of all possible answer types" in {
        status(postQuestion("A?", AnswerType.Text)) must equal(201)
        status(postQuestion("B1?", AnswerType.RangeContinuous)) must equal(201)
        status(postQuestion("B2?", AnswerType.RangeContinuous, answerRange = Some(InclusiveRange(0.2, 3.5)))) must equal(201)
        status(postQuestion("C?", AnswerType.RangeDiscrete, answerRange = Some(InclusiveRange(1, 5)))) must equal(201)
        status(postQuestion("D?", AnswerType.MultipleChoiceSingle)) must equal(201)
        status(postQuestion("D?", AnswerType.MultipleChoiceMany)) must equal(201)
      }

      "accept questions with correct answer labels" in {
        val responseA = postQuestion("Foo?", AnswerType.RangeContinuous, Some(Seq("this is min", "this is max")))
        status(responseA) must equal(CREATED)
        val listA = contentAsJson(responseA).apply("answerLabels").as[JsArray].value.map(_.as[String])
        listA.length must equal(2)
        listA must contain allOf("this is min", "this is max")

        val responseB = postQuestion("Bar?", AnswerType.RangeDiscrete, Some(Seq("a", "b", "c", "d", "e")), Some(InclusiveRange(3, 7)))
        status(responseB) must equal(CREATED)
        val listB = contentAsJson(responseB).apply("answerLabels").as[JsArray].value.map(_.as[String])
        listB.length must equal(5)
        listB must contain allOf("a", "b", "c", "d", "e")

        val responseC = postQuestion("Baz?", AnswerType.MultipleChoiceSingle, Some(Seq("I", "II", "III")))
        status(responseC) must equal(CREATED)
        val listC = contentAsJson(responseC).apply("answerLabels").as[JsArray].value.map(_.as[String])
        listC.length must equal(3)
        listC must contain allOf("I", "II", "III")

        val responseD = postQuestion("Baz?", AnswerType.MultipleChoiceMany, Some(Seq("I", "II", "III")))
        status(responseD) must equal(CREATED)
        val listD = contentAsJson(responseD).apply("answerLabels").as[JsArray].value.map(_.as[String])
        listD.length must equal(3)
        listD must contain allOf("I", "II", "III")
      }

      "preserve empty answer labels" in {
        val responseA = postQuestion("Foo?", AnswerType.RangeContinuous, Some(Seq("", "")))
        status(responseA) must equal(CREATED)

        val listA = contentAsJson(responseA).apply("answerLabels").as[JsArray].value.map(_.as[String])
        listA.length must equal(2)
        listA(0) must equal("")
        listA(1) must equal("")
      }

      "reject reading questions in non-accessible study" in {
        val question = doSync(createQuestion("This is not accessible?", questionnaireId = Some(hiddenQuestionnaire.id)))

        val response = doAuthenticatedRequest(GET, s"/v1/questions/${question.id}")
        status(response) must equal(FORBIDDEN)
      }

      "reject listing questions in non-accessible study" in {
        val question = doSync(createQuestion("This is not accessible?", questionnaireId = Some(hiddenQuestionnaire.id)))

        val response = doAuthenticatedRequest(GET, s"/v1/questions")
        val ids = contentAsJson(response).as[JsArray].value.map(_ ("id").as[Long])
        ids must not contain question.id
      }

      "reject adding questions to non-writeable study" in {
        val response = doAuthenticatedRequest(POST, s"/v1/questions",
          Some(questionJson("Sample Question", questionnaireId = Some(readableQuestionnaire.id))))
        status(response) must equal(FORBIDDEN)
      }

      "reject updating questions in non-writeable study" in {
        val question = doSync(createQuestion("This is not accessible?", questionnaireId = Some(readableQuestionnaire.id)))

        val response = doAuthenticatedRequest(PUT, s"/v1/questions/${question.id}", Some(questionJson("Sample Question")))
        status(response) must equal(FORBIDDEN)
      }

      "reject removing questions from non-writeable study" in {
        val question = doSync(createQuestion("This is not accessible?", questionnaireId = Some(readableQuestionnaire.id)))

        val response = doAuthenticatedRequest(DELETE, s"/v1/questions/${question.id}")
        status(response) must equal(FORBIDDEN)
      }
    }

    "logged in as admin" should {
      implicit val _ = Role.Admin

      "reject questions with non-existent questionnaire id" in {
        val response = doAuthenticatedRequest(POST, "/v1/questions", Some(questionJson("Foo bar?", questionnaireId = Some(666))))
        status(response) must equal(BAD_REQUEST)
      }
    }
  }

  private def createQuestion(
    text: String,
    answerType: AnswerType = AnswerType.Text,
    answerLabels: Option[Seq[String]] = None,
    answerRange: Option[InclusiveRange[BigDecimal]] = None,
    questionnaireId: Option[Long] = None
  ): Future[Question] =
    questionsRepo.add(Question(0, text, answerType,
      answerLabels.map(Serialization.serialize), answerRange.map(r => Json.toJson(r).toString()), questionnaireId))

  private def postQuestion(
    text: String,
    answerType: AnswerType = AnswerType.Text,
    answerLabels: Option[Seq[String]] = None,
    answerRange: Option[InclusiveRange[BigDecimal]] = None
  ): Future[Result] = {
    val postRequest = AuthenticatedFakeRequest(Role.Researcher)(POST, "/v1/questions")
      .withBody(questionJson(text, answerType.toString, answerLabels, answerRange))
    route(app, postRequest).get
  }

  private def deleteQuestion(id: Long) = {
    val deleteRequest = AuthenticatedFakeRequest(Role.Researcher)(DELETE, s"/v1/questions/$id")
    route(app, deleteRequest).get
  }

  private def questionJson(
    text: String,
    answerType: String = AnswerType.Text.toString,
    answerLabels: Option[Seq[String]] = None,
    answerRange: Option[InclusiveRange[BigDecimal]] = None,
    questionnaireId: Option[Long] = None
  ): JsValue = {
    var obj: JsObject = Json.obj(
      "content" -> text,
      "answerType" -> answerType
    )
    obj = if (answerLabels.isDefined) obj + ("answerLabels" -> JsArray(answerLabels.get.map(JsString))) else obj
    obj = if (questionnaireId.isDefined) obj + ("questionnaireId" -> Json.toJson(questionnaireId.get)) else obj
    obj = if (answerRange.isDefined) obj + ("answerRange" -> Json.toJson(answerRange.get)) else obj
    obj
  }
}
