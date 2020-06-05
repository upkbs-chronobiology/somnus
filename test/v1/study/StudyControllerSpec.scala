package v1.study

import scala.concurrent.ExecutionContext.Implicits.global

import auth.AuthService
import auth.roles.Role
import models.AccessLevel
import models.Questionnaire
import models.QuestionnairesRepository
import models.Study
import models.StudyAccess
import models.StudyAccessRepository
import models.StudyRepository
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.test.Helpers._
import play.api.test.Injecting
import testutil.Authenticated
import testutil.FreshDatabase
import testutil.TestUtils
import util.Futures.IterableFutureExtensions

class StudyControllerSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with Injecting
    with FreshDatabase
    with TestUtils
    with Authenticated
    with BeforeAndAfterEach {

  private val studyAccessRepo = inject[StudyAccessRepository]
  private val studyRepo = inject[StudyRepository]
  private val questionnairesRepo = inject[QuestionnairesRepository]

  private val authService = inject[AuthService]

  override def afterEach(): Unit = {
    super.afterEach()

    // delete all studies (and their participants, and questionnaires)
    doSync(
      studyRepo
        .listAll()
        .mapIterableAsync(s => {
          studyRepo
            .listParticipants(s.id)
            .mapIterableAsync(p => studyRepo.removeParticipant(s.id, p.id))
            .flatMap(
              _ =>
                questionnairesRepo
                  .listByStudy(s.id)
                  .mapIterableAsync(q => questionnairesRepo.delete(q.id))
                  .flatMap(_ => studyRepo.delete(s.id))
            )
        })
    )
  }

  "StudyController" when {
    "not logged in" should {
      "reject requests" in {
        status(doRequest(GET, "/v1/studies")) must equal(401)
        status(doRequest(GET, "/v1/studies/99")) must equal(401)
        status(doRequest(POST, "/v1/studies", Some(studyJson("Foo Study")))) must equal(401)
        status(doRequest(PUT, "/v1/studies/88", Some(studyJson("Bar Study")))) must equal(401)
        status(doRequest(DELETE, "/v1/studies/77")) must equal(401)
      }
    }

    "logged in as basic user" should {
      "reject reading" in {
        val study = doSync(studyRepo.create(Study(0, "test study")))

        status(doAuthenticatedRequest(GET, "/v1/studies")) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(GET, s"/v1/studies/${study.id}")) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(GET, s"/v1/studies/${study.id}/questionnaires")) must equal(FORBIDDEN)
      }

      "reject altering" in {
        status(doAuthenticatedRequest(POST, "/v1/studies", Some(studyJson("Blabla Study")))) must equal(403)
        status(doAuthenticatedRequest(PUT, "/v1/studies/88", Some(studyJson("Yada Study")))) must equal(403)
        status(doAuthenticatedRequest(DELETE, "/v1/studies/77")) must equal(403)
      }
    }

    "logged in as researcher" should {
      implicit val r = Role.Researcher

      "list only accessible studies" in {
        doSync(studyRepo.create(Study(0, "hidden study")))
        val readStudy = doSync(studyRepo.create(Study(0, "read study")))
        val writeStudy = doSync(studyRepo.create(Study(0, "write study")))
        val ownStudy = doSync(studyRepo.create(Study(0, "own study")))
        doSync(studyAccessRepo.upsert(StudyAccess(researchUser.id, readStudy.id, AccessLevel.Read)))
        doSync(studyAccessRepo.upsert(StudyAccess(researchUser.id, writeStudy.id, AccessLevel.Write)))
        doSync(studyAccessRepo.upsert(StudyAccess(researchUser.id, ownStudy.id, AccessLevel.Own)))

        val response = doAuthenticatedRequest(GET, "/v1/studies")

        status(response) must equal(OK)
        val ids = contentAsJson(response).as[JsArray].value.map(_("id").as[Long])
        ids must contain theSameElementsAs Seq(readStudy.id, writeStudy.id, ownStudy.id)
      }

      "allow reading with read access" in {
        val study = doSync(studyRepo.create(Study(0, "test study")))
        doSync(studyAccessRepo.upsert(StudyAccess(researchUser.id, study.id, AccessLevel.Read)))

        val readStudy = contentAsJson(doAuthenticatedRequest(GET, s"/v1/studies/${study.id}"))
        readStudy("name").as[String] must equal("test study")

        val readStudies = contentAsJson(doAuthenticatedRequest(GET, "/v1/studies"))
        readStudies.as[JsArray].value.length must equal(1)
      }

      "reject reading without access rights" in {
        val study = doSync(studyRepo.create(Study(0, "test study")))
        status(doAuthenticatedRequest(GET, s"/v1/studies/${study.id}")) must equal(FORBIDDEN)
      }

      "deliver questionnaires for a study with read access" in {
        val study = doSync(studyRepo.create(Study(0, "Xyz Study")))
        doSync(studyAccessRepo.upsert(StudyAccess(researchUser.id, study.id, AccessLevel.Read)))

        val questionnaireA = doSync(questionnairesRepo.create(Questionnaire(0, "Questionnaire A", Some(study.id))))
        val questionnaireB = doSync(questionnairesRepo.create(Questionnaire(0, "Questionnaire B", Some(study.id))))

        val response = doAuthenticatedRequest(GET, s"/v1/studies/${study.id}/questionnaires")
        status(response) must equal(200)

        val list = contentAsJson(response).as[JsArray].value
        list.length must equal(2)
        list.map(item => item("name").as[String]) must contain allOf ("Questionnaire A", "Questionnaire B")
      }

      "reject questionnaire listing without study access" in {
        val study = doSync(studyRepo.create(Study(0, "Xyz Study")))

        doSync(questionnairesRepo.create(Questionnaire(0, "Questionnaire A", Some(study.id))))
        doSync(questionnairesRepo.create(Questionnaire(0, "Questionnaire B", Some(study.id))))

        status(doAuthenticatedRequest(GET, s"/v1/studies/${study.id}/questionnaires")) must equal(FORBIDDEN)
      }

      "allow all CRUD operations with write access" in {
        contentAsJson(doAuthenticatedRequest(GET, "/v1/studies")).as[JsArray].value.length must equal(0)

        val creationResult = doAuthenticatedRequest(POST, "/v1/studies", Some(studyJson("Foo Bar")))
        status(creationResult) must equal(201)
        val createdId = contentAsJson(creationResult).apply("id").as[Long]

        contentAsJson(doAuthenticatedRequest(GET, "/v1/studies")).as[JsArray].value.length must equal(1)
        contentAsJson(doAuthenticatedRequest(GET, s"/v1/studies/$createdId")).apply("name").as[String] must equal(
          "Foo Bar"
        )

        status(doAuthenticatedRequest(PUT, s"/v1/studies/$createdId", Some(studyJson("My New Name")))) must equal(200)

        contentAsJson(doAuthenticatedRequest(GET, s"/v1/studies/$createdId")).apply("name").as[String] must equal(
          "My New Name"
        )

        status(doAuthenticatedRequest(DELETE, s"/v1/studies/$createdId")) must equal(200)

        contentAsJson(doAuthenticatedRequest(GET, "/v1/studies")).as[JsArray].value.length must equal(0)
      }

      "reject modification requests with read-only access" in {
        val study = doSync(studyRepo.create(Study(0, "my study")))
        doSync(studyAccessRepo.upsert(StudyAccess(researchUser.id, study.id, AccessLevel.Read)))

        status(doAuthenticatedRequest(PUT, s"/v1/studies/${study.id}", Some(studyJson("my new name")))) must equal(
          FORBIDDEN
        )
        status(doAuthenticatedRequest(DELETE, s"/v1/studies/${study.id}")) must equal(FORBIDDEN)
      }

      "reject modification requests with no access" in {
        val study = doSync(studyRepo.create(Study(0, "my study")))

        status(doAuthenticatedRequest(PUT, s"/v1/studies/${study.id}", Some(studyJson("my new name")))) must equal(
          FORBIDDEN
        )
        status(doAuthenticatedRequest(DELETE, s"/v1/studies/${study.id}")) must equal(FORBIDDEN)
      }

      "make study creator an owner" in {
        val study = contentAsJson(doAuthenticatedRequest(POST, "/v1/studies", Some(studyJson("Foo Bar"))))

        val access = doSync(studyAccessRepo.read(researchUser.id, study("id").as[Long]))
        access.map(_.level) must equal(Some(AccessLevel.Own))
      }

      "reject updates with non-existent ids" in {
        status(doAuthenticatedRequest(PUT, "/v1/studies/666", Some(studyJson("Out Of Ideas")))) must equal(FORBIDDEN)
      }

      "list correct participants after adding and removing" in {
        val creationResult = doAuthenticatedRequest(POST, "/v1/studies", Some(studyJson("Heavy Light Study")))
        status(creationResult) must equal(201)
        val createdId = contentAsJson(creationResult).apply("id").as[Long]

        val george = doSync(authService.register("George Wilson", Some("iamgeorge")))

        status(doAuthenticatedRequest(PUT, s"/v1/studies/$createdId/participants/${george.id}")) must equal(201)

        val list = contentAsJson(doAuthenticatedRequest(GET, s"/v1/studies/$createdId/participants")).as[JsArray].value
        list.length must equal(1)
        list.head.apply("name").as[String] must equal("George Wilson")

        status(doAuthenticatedRequest(DELETE, s"/v1/studies/$createdId/participants/${george.id}")) must equal(200)

        val finalList =
          contentAsJson(doAuthenticatedRequest(GET, s"/v1/studies/$createdId/participants")).as[JsArray].value
        finalList.length must equal(0)
      }

      "reject listing participants without access" in {
        val study = doSync(studyRepo.create(Study(0, "hidden study")))
        status(doAuthenticatedRequest(GET, s"/v1/studies/${study.id}/participants")) must equal(FORBIDDEN)
      }

      "reject modifying participants with read-only access" in {
        val study = doSync(studyRepo.create(Study(0, "hidden study")))
        val participant = doSync(authService.register("Wanno Tschoin", Some("iamgeorge")))

        val addResponse = doAuthenticatedRequest(PUT, s"/v1/studies/${study.id}/participants/${participant.id}")
        status(addResponse) must equal(FORBIDDEN)

        studyRepo.addParticipant(study.id, participant.id)

        val delResponse = doAuthenticatedRequest(DELETE, s"/v1/studies/${study.id}/participants/${participant.id}")
        status(delResponse) must equal(FORBIDDEN)
      }
    }

    "logged in as admin" should {
      implicit val r = Role.Admin

      "reject updates with non-existent ids" in {
        status(doAuthenticatedRequest(PUT, "/v1/studies/666", Some(studyJson("Out Of Ideas")))) must equal(BAD_REQUEST)
      }
    }
  }

  private def studyJson(name: String): JsValue = {
    Json.obj("name" -> name)
  }

}
