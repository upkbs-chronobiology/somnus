package v1.questionnaire

import auth.roles.Role
import models.AccessLevel
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
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.JsArray
import play.api.libs.json.JsNumber
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.test.Helpers._
import play.api.test.Injecting
import testutil.Authenticated
import testutil.FreshDatabase
import testutil.TestUtils

class QuestionnaireControllerSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with Injecting
    with FreshDatabase
    with TestUtils
    with Authenticated
    with BeforeAndAfterAll {

  private val studyRepo = inject[StudyRepository]
  private val questionnairesRepo = inject[QuestionnairesRepository]
  private val questionsRepo = inject[QuestionsRepository]

  private lazy val hiddenStudy = doSync(studyRepo.create(Study(0, "Hidden Study")))
  private lazy val readableStudy = doSync(studyRepo.create(Study(0, "Readable Study")))
  private lazy val writeableStudy = doSync(studyRepo.create(Study(0, "Writeable Study")))
  private lazy val hiddenQuestionnaire = doSync(
    questionnairesRepo.create(Questionnaire(0, "Hidden Questionnaire", Some(hiddenStudy.id)))
  )
  private lazy val readableQuestionnaire = doSync(
    questionnairesRepo.create(Questionnaire(0, "Readable Questionnaire", Some(readableStudy.id)))
  )
  private lazy val writeableQuestionnaire = doSync(
    questionnairesRepo.create(Questionnaire(0, "Writeable Questionnaire", Some(writeableStudy.id)))
  )
  private lazy val hiddenQuestion = doSync(
    questionsRepo
      .add(Question(0, "Hidden Question", AnswerType.RangeDiscrete, None, Some("1,5"), Some(hiddenQuestionnaire.id)))
  )
  private lazy val readableQuestion = doSync(
    questionsRepo.add(
      Question(0, "Readable Question", AnswerType.RangeDiscrete, None, Some("1,5"), Some(readableQuestionnaire.id))
    )
  )

  override def beforeAll(): Unit = {
    super.beforeAll()

    doSync(studyRepo.addParticipant(readableStudy.id, baseUser.id))

    val aclRepo = inject[StudyAccessRepository]
    doSync(aclRepo.upsert(StudyAccess(researchUser.id, readableStudy.id, AccessLevel.Read)))
    doSync(aclRepo.upsert(StudyAccess(researchUser.id, writeableStudy.id, AccessLevel.Write)))

    // touch those to make sure they get initialized
    readableQuestion
  }

  "QuestionnaireController" when {
    "not logged in" should {
      "reject requests" in {
        status(doRequest(GET, s"/v1/questionnaires")) must equal(UNAUTHORIZED)
        status(doRequest(POST, s"/v1/questionnaires", Some(qJson("Foo bar", None)))) must equal(UNAUTHORIZED)
        status(doRequest(PUT, s"/v1/questionnaires/${readableQuestionnaire.id}", Some(qJson("Foo bar", None)))) must equal(
          UNAUTHORIZED
        )
        status(doRequest(DELETE, s"/v1/questionnaires/${readableQuestionnaire.id}")) must equal(UNAUTHORIZED)
        status(doRequest(POST, s"/v1/questionnaires/${readableQuestionnaire.id}/duplicate")) must equal(UNAUTHORIZED)
        status(doRequest(GET, s"/v1/questionnaires/${readableQuestionnaire.id}/questions")) must equal(UNAUTHORIZED)
      }
    }

    "logged in as base user" should {
      "reject index and editing requests" in {
        status(doAuthenticatedRequest(GET, s"/v1/questionnaires")) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(POST, s"/v1/questionnaires", Some(qJson("Foo bar", None)))) must equal(FORBIDDEN)
        status(
          doAuthenticatedRequest(PUT, s"/v1/questionnaires/${readableQuestionnaire.id}", Some(qJson("Foo bar", None)))
        ) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(DELETE, s"/v1/questionnaires/${readableQuestionnaire.id}")) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(POST, s"/v1/questionnaires/${readableQuestionnaire.id}/duplicate")) must equal(
          FORBIDDEN
        )
      }

      "list questions for specific questionnaire where participant" in {
        val response = doAuthenticatedRequest(GET, s"/v1/questionnaires/${readableQuestionnaire.id}/questions")

        status(response) must equal(OK)
        val list = contentAsJson(response).as[JsArray].value
        list.map(_("id").as[Long]) must contain only readableQuestion.id
      }

      "reject listing questions for questionnaire where not participant" in {
        status(doAuthenticatedRequest(GET, s"/v1/questionnaires/${hiddenQuestionnaire.id}/questions")) must equal(
          FORBIDDEN
        )
      }
    }

    "logged in as researcher" should {
      implicit val _ = Role.Researcher

      "list questionnaires with study access only" in {
        val result = doAuthenticatedRequest(GET, s"/v1/questionnaires")

        status(result) must equal(OK)

        val list = contentAsJson(result).as[JsArray].value
        list.map(_("name").as[String]) must contain("Readable Questionnaire")
        list.map(_("id").as[Long]) must not contain (hiddenQuestionnaire.id)
      }

      "list questions" in {
        val response = doAuthenticatedRequest(GET, s"/v1/questionnaires/${readableQuestionnaire.id}/questions")

        status(response) must equal(OK)
        val questions = contentAsJson(response).as[JsArray].value
        questions.map(_("content").as[String]) must contain("Readable Question")
        questions.map(_("id").as[Long]) must not contain (hiddenQuestion.id)
      }

      "reject listing questions for non-readable questionnaire" in {
        val response = doAuthenticatedRequest(GET, s"/v1/questionnaires/${hiddenQuestionnaire.id}/questions")
        status(response) must equal(FORBIDDEN)
      }

      "create, update and delete questionnaires" in {
        val initialNum = doSync(questionnairesRepo.listAll()).length

        val postResult = doAuthenticatedRequest(POST, s"/v1/questionnaires", Some(qJson("Bar foo", None)))
        status(postResult) must equal(CREATED)
        val listAfterPost = doSync(questionnairesRepo.listAll())
        listAfterPost.length - initialNum must equal(1)
        listAfterPost.map(q => q.name) must contain("Bar foo")

        val newId = contentAsJson(postResult).apply("id").as[Long]

        status(doAuthenticatedRequest(PUT, s"/v1/questionnaires/$newId", Some(qJson("Bar foo 2", None)))) must equal(OK)
        val listAfterPut = doSync(questionnairesRepo.listAll())
        listAfterPut.length - initialNum must equal(1)
        listAfterPut.map(q => q.name) mustNot contain("Bar foo")
        listAfterPut.map(q => q.name) must contain("Bar foo 2")

        status(doAuthenticatedRequest(DELETE, s"/v1/questionnaires/$newId")) must equal(OK)
        val listAfterDelete = doSync(questionnairesRepo.listAll())
        listAfterDelete.length - initialNum must equal(0)
        listAfterDelete.map(q => q.name) mustNot contain("Bar foo")
        listAfterDelete.map(q => q.name) mustNot contain("Bar foo 2")
      }

      "reject modifying read-only questionnaire" in {
        val response = doAuthenticatedRequest(
          PUT,
          s"/v1/questionnaires/${readableQuestionnaire.id}",
          Some(qJson("another name", Some(readableStudy.id)))
        )
        status(response) must equal(FORBIDDEN)

        status(doAuthenticatedRequest(DELETE, s"/v1/questionnaires/${readableQuestionnaire.id}")) must equal(FORBIDDEN)
      }

      "reject adding questionnaires to read-only studies" in {
        val response =
          doAuthenticatedRequest(POST, "/v1/questionnaires", Some(qJson("some name", Some(readableStudy.id))))
        status(response) must equal(FORBIDDEN)
      }

      "reject re-assigning questionnaires from read-only studies" in {
        val response = doAuthenticatedRequest(
          PUT,
          s"/v1/questionnaires/${readableQuestionnaire.id}",
          Some(qJson("another name", Some(writeableStudy.id)))
        )
        status(response) must equal(FORBIDDEN)
      }

      "reject re-assigning questionnaires to read-only studies" in {
        val response = doAuthenticatedRequest(
          PUT,
          s"/v1/questionnaires/${writeableQuestionnaire.id}",
          Some(qJson("another name", Some(readableStudy.id)))
        )
        status(response) must equal(FORBIDDEN)
      }

      "give error status when trying to delete non-existent questionnaire" in {
        status(doAuthenticatedRequest(DELETE, "/v1/questionnaires/999")) must equal(FORBIDDEN)
      }

      "reject deleting questionnaires containing questions" in {
        status(doAuthenticatedRequest(DELETE, s"/v1/questionnaires/${readableQuestionnaire.id}")) must equal(FORBIDDEN)
      }

      "refuse to duplicate inexistent questionnaires" in {
        status(doAuthenticatedRequest(POST, "/v1/questionnaires/999/duplicate")) must equal(FORBIDDEN)
      }

      "duplicate existing questionnaires" in {
        val response = doAuthenticatedRequest(POST, s"/v1/questionnaires/${readableQuestionnaire.id}/duplicate")
        status(response) must equal(CREATED)

        val dupeJson = contentAsJson(response)
        // TODO: More generic comparison (all properties except id)
        dupeJson("id").as[Long] must not equal readableQuestionnaire.id
        dupeJson("name").as[String] must equal(readableQuestionnaire.name)
        dupeJson("studyId").as[Long] must equal(readableQuestionnaire.studyId.get)

        val dupeQuestions = doSync(questionsRepo.listByQuestionnaire(dupeJson("id").as[Long]))
        dupeQuestions.size must equal(1)
        // TODO: More generic comparison (all properties except id)
        dupeQuestions.head.id must not equal readableQuestion.id
        dupeQuestions.head.content must equal(readableQuestion.content)
        dupeQuestions.head.answerType must equal(readableQuestion.answerType)
        dupeQuestions.head.answerLabels must equal(readableQuestion.answerLabels)
        dupeQuestions.head.answerRange must equal(readableQuestion.answerRange)
        dupeQuestions.head.questionnaireId.get must equal(dupeJson("id").as[Long])
      }

      "reject duplicating non-readable questionnaire" in {
        val response = doAuthenticatedRequest(POST, s"/v1/questionnaires/${hiddenQuestionnaire.id}/duplicate")
        status(response) must equal(FORBIDDEN)
      }
    }

    "logged in as admin" should {
      implicit val _ = Role.Admin

      "list all questionnaires" in {
        val ids =
          contentAsJson(doAuthenticatedRequest(GET, s"/v1/questionnaires")).as[JsArray].value.map(_("id").as[Long])
        ids must contain allOf (hiddenQuestionnaire.id, readableQuestionnaire.id, writeableQuestionnaire.id)
      }

      "modify questionnaires without explicit acls" in {
        val response = doAuthenticatedRequest(
          PUT,
          s"/v1/questionnaires/${hiddenQuestionnaire.id}",
          Some(qJson("another name", Some(hiddenStudy.id)))
        )
        status(response) must equal(OK)
      }

      "duplicate questionnaires without explicit acls" in {
        val response = doAuthenticatedRequest(POST, s"/v1/questionnaires/${hiddenQuestionnaire.id}/duplicate")
        status(response) must equal(CREATED)
      }

      "give error status when trying to delete non-existent questionnaire" in {
        status(doAuthenticatedRequest(DELETE, "/v1/questionnaires/999")) must equal(NOT_FOUND)
      }

      "reject deleting questionnaires containing questions" in {
        status(doAuthenticatedRequest(DELETE, s"/v1/questionnaires/${readableQuestionnaire.id}")) must equal(
          BAD_REQUEST
        )
      }

      "refuse to duplicate inexistent questionnaires" in {
        status(doAuthenticatedRequest(POST, "/v1/questionnaires/999/duplicate")) must equal(BAD_REQUEST)
      }
    }
  }

  private def qJson(name: String, studyId: Option[Long]): JsValue = {
    val obj = Json.obj("name" -> name)
    if (studyId.isEmpty) obj else obj + ("studyId" -> JsNumber(studyId.get))
  }
}
