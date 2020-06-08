package v1.schedule

import java.time.LocalDate
import java.time.LocalTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import auth.roles.Role
import models.AccessLevel
import models.Questionnaire
import models.QuestionnairesRepository
import models.Schedule
import models.SchedulesRepository
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
import play.api.test.Helpers._
import play.api.test.Injecting
import testutil.Authenticated
import testutil.FreshDatabase
import testutil.TestUtils

class ScheduleControllerSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with Injecting
    with FreshDatabase
    with TestUtils
    with Authenticated
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  private lazy val schedulesRepo = inject[SchedulesRepository]

  private lazy val writeableStudy = doSync(inject[StudyRepository].create(Study(0, "My Study W")))
  private lazy val readableStudy = doSync(inject[StudyRepository].create(Study(0, "My Study R")))
  private lazy val unrelatedStudy = doSync(inject[StudyRepository].create(Study(0, "My Study N")))

  private lazy val questionnairesRepo: QuestionnairesRepository = inject[QuestionnairesRepository]
  private lazy val writeableQuestionnaire = doSync(
    questionnairesRepo.create(Questionnaire(0, "Testionnaire W", Some(writeableStudy.id)))
  )
  private lazy val readableQuestionnaire = doSync(
    questionnairesRepo.create(Questionnaire(0, "Testionnaire R", Some(readableStudy.id)))
  )
  private lazy val unrelatedQuestionnaire = doSync(
    questionnairesRepo.create(Questionnaire(0, "Testionnaire N", Some(unrelatedStudy.id)))
  )
  private lazy val disconnectedQuestionnaire = doSync(questionnairesRepo.create(Questionnaire(0, "Freeonaire", None)))

  override def beforeAll(): Unit = {
    super.beforeAll()

    val studyAccessRepo: StudyAccessRepository = inject[StudyAccessRepository]
    doSync(studyAccessRepo.upsert(StudyAccess(researchUser.id, writeableStudy.id, AccessLevel.Write)))
    doSync(studyAccessRepo.upsert(StudyAccess(researchUser.id, readableStudy.id, AccessLevel.Read)))
    doSync(studyAccessRepo.upsert(StudyAccess(adminUser.id, writeableStudy.id, AccessLevel.Write)))
    doSync(studyAccessRepo.upsert(StudyAccess(adminUser.id, readableStudy.id, AccessLevel.Read)))
  }

  override def afterEach(): Unit = {
    super.afterEach()

    // delete all schedules (linked to a questionnaire)
    doSync(
      questionnairesRepo
        .listAll()
        .flatMap(
          qs =>
            Future.sequence(qs.flatMap(questionnaire => {
              val schedules = doSync(schedulesRepo.getByQuestionnaire(questionnaire.id))
              schedules.map(s => schedulesRepo.delete(s.id))
            }))
        )
    )
  }

  "ScheduleController" when {
    "not logged in" should {
      "reject any request" in {
        status(doRequest(GET, s"/v1/users/me/schedules")) must equal(UNAUTHORIZED)
        status(doRequest(GET, s"/v1/users/123/schedules")) must equal(UNAUTHORIZED)
        status(doRequest(GET, s"/v1/questionnaires/456/schedules")) must equal(UNAUTHORIZED)
        val payload = Json.toJson("irrelephant")
        status(doRequest(POST, s"/v1/schedules", Some(payload))) must equal(UNAUTHORIZED)
        status(doRequest(PUT, s"/v1/schedules/789", Some(payload))) must equal(UNAUTHORIZED)
        status(doRequest(DELETE, s"/v1/schedules/789")) must equal(UNAUTHORIZED)
      }
    }

    "logged in as base user" should {
      "list my schedules" in {
        val listBeforeCreation = contentAsJson(doAuthenticatedRequest(GET, s"/v1/users/me/schedules")).as[JsArray].value
        listBeforeCreation.length must equal(0)

        val schedule =
          scheduleJson(writeableQuestionnaire.id, baseUser.id, "2019-10-20", "2019-11-05", "10:00:00", "23:45:07", 10)
        status(doAuthenticatedRequest(POST, "/v1/schedules", Some(schedule), role = Some(Role.Researcher))) must equal(
          CREATED
        )

        val listAfterCreation = contentAsJson(doAuthenticatedRequest(GET, s"/v1/users/me/schedules")).as[JsArray].value
        listAfterCreation.length must equal(1)
        listAfterCreation(0)("userId").as[Long] must equal(baseUser.id)
      }

      "reject listing other users' schedules" in {
        status(doAuthenticatedRequest(GET, s"/v1/users/${researchUser.id}/schedules")) must equal(FORBIDDEN)
      }

      "reject modification requests" in {
        val payload = Json.toJson("irrelephant")
        status(doAuthenticatedRequest(POST, s"/v1/schedules", Some(payload))) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(PUT, s"/v1/schedules/123", Some(payload))) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(DELETE, s"/v1/schedules/123")) must equal(FORBIDDEN)
      }
    }

    "logged in as researcher" should {
      implicit val r = Role.Researcher

      "create, update and delete items" in {
        val schedule =
          scheduleJson(writeableQuestionnaire.id, baseUser.id, "2018-10-20", "2018-11-05", "10:00:00", "23:45:07", 10)
        val creationResponse = doAuthenticatedRequest(POST, "/v1/schedules", Some(schedule))

        status(creationResponse) must equal(CREATED)
        val createdSchedule = contentAsJson(creationResponse)
        val createdId = createdSchedule("id").as[Long]

        createdId must be >= 0L
        createdSchedule("questionnaireId").as[Long] must equal(writeableQuestionnaire.id)
        createdSchedule("userId").as[Long] must equal(baseUser.id)
        createdSchedule("startDate").as[String] must equal("2018-10-20")
        createdSchedule("endDate").as[String] must equal("2018-11-05")
        createdSchedule("startTime").as[String] must equal("10:00:00")
        createdSchedule("endTime").as[String] must equal("23:45:07")
        createdSchedule("frequency").as[Int] must equal(10)

        val listAfterCreation = contentAsJson(
          doAuthenticatedRequest(GET, s"/v1/questionnaires/${writeableQuestionnaire.id}/schedules")
        ).as[JsArray].value
        listAfterCreation.length must equal(1)
        listAfterCreation(0)("id").as[Long] must equal(createdId)

        val secondSchedule =
          scheduleJson(writeableQuestionnaire.id, baseUser.id, "2019-10-20", "2019-11-05", "10:00:00", "23:45:07", 20)
        val updateResponse = doAuthenticatedRequest(PUT, s"/v1/schedules/$createdId", Some(secondSchedule))

        status(updateResponse) must equal(OK)
        val updatedSchedule = contentAsJson(updateResponse)
        updatedSchedule("id").as[Long] must be >= 0L
        updatedSchedule("questionnaireId").as[Long] must equal(writeableQuestionnaire.id)
        updatedSchedule("userId").as[Long] must equal(baseUser.id)
        updatedSchedule("startDate").as[String] must equal("2019-10-20")
        updatedSchedule("endDate").as[String] must equal("2019-11-05")
        updatedSchedule("startTime").as[String] must equal("10:00:00")
        updatedSchedule("endTime").as[String] must equal("23:45:07")
        updatedSchedule("frequency").as[Int] must equal(20)

        val deleteResponse = doAuthenticatedRequest(DELETE, s"/v1/schedules/$createdId")

        status(deleteResponse) must equal(OK)

        val listAfterDeletion = contentAsJson(
          doAuthenticatedRequest(GET, s"/v1/questionnaires/${writeableQuestionnaire.id}/schedules")
        ).as[JsArray].value
        listAfterDeletion.length must equal(0)
      }

      "reject adding if already present" in {
        val schedule =
          scheduleJson(writeableQuestionnaire.id, baseUser.id, "2018-10-20", "2018-11-05", "10:00:00", "23:45:07", 10)
        val creationResponse = doAuthenticatedRequest(POST, "/v1/schedules", Some(schedule))
        status(creationResponse) must equal(CREATED)

        val reSchedule =
          scheduleJson(writeableQuestionnaire.id, baseUser.id, "2019-11-15", "2019-12-20", "08:00:00", "11:22:42", 66)
        val reCreationResponse = doAuthenticatedRequest(POST, "/v1/schedules", Some(reSchedule))
        status(reCreationResponse) must equal(BAD_REQUEST)
      }

      "only create 1 schedule in case of many repeating requests" in {
        val schedule =
          scheduleJson(writeableQuestionnaire.id, baseUser.id, "2018-10-20", "2018-11-05", "10:00:00", "23:45:07", 10)
        val createdCount = Future
          .sequence(List.fill(10)(doAuthenticatedRequest(POST, "/v1/schedules", Some(schedule))))
          .map(
            resultList =>
              (resultList.toStream.map(r => r.header.status) map {
                case CREATED => 1
                case _ => 0
              }).sum
          )
        doSync(createdCount) must equal(1)

        val schedules = doSync(schedulesRepo.getByQuestionnaire(writeableQuestionnaire.id))
        schedules.length must equal(1)
      }

      "reject badly formatted dates" in {
        val schedule =
          scheduleJson(writeableQuestionnaire.id, baseUser.id, "10/20/2018", "5/11/2018", "10:00:00", "23:45:07", 10)
        status(doAuthenticatedRequest(POST, "/v1/schedules", Some(schedule))) must equal(BAD_REQUEST)
      }

      "reject badly formatted times" in {
        val schedule =
          scheduleJson(writeableQuestionnaire.id, baseUser.id, "2019-10-20", "2019-11-05", "10.00", "23.45", 10)
        status(doAuthenticatedRequest(POST, "/v1/schedules", Some(schedule))) must equal(BAD_REQUEST)
      }

      "grant write access to disconnected questionnaires" in {
        val schedule = scheduleJson(
          disconnectedQuestionnaire.id,
          baseUser.id,
          "2018-10-20",
          "2018-11-05",
          "10:00:00",
          "23:45:07",
          10
        )
        val creationResponse = doAuthenticatedRequest(POST, "/v1/schedules", Some(schedule))
        status(creationResponse) must equal(CREATED)
      }

      "reject read access to unrelated questionnaires" in {
        val response = doAuthenticatedRequest(GET, s"/v1/questionnaires/${unrelatedQuestionnaire.id}/schedules")
        status(response) must equal(FORBIDDEN)
      }

      "reject write access to read-only questionnaires" in {
        val schedule =
          scheduleJson(readableQuestionnaire.id, baseUser.id, "2018-10-20", "2018-11-05", "10:00:00", "23:45:07", 10)
        val creationResponse = doAuthenticatedRequest(POST, "/v1/schedules", Some(schedule))
        status(creationResponse) must equal(FORBIDDEN)
      }

      "list by user only schedules where study is readable" in {
        val readableSchedule = doSync(schedulesRepo.create(sampleSchedule(readableQuestionnaire.id, baseUser.id)))
        val writeableSchedule = doSync(schedulesRepo.create(sampleSchedule(writeableQuestionnaire.id, baseUser.id)))
        /* hiddenSchedule = */
        doSync(schedulesRepo.create(sampleSchedule(unrelatedQuestionnaire.id, baseUser.id)))

        val response = doAuthenticatedRequest(GET, s"/v1/users/${baseUser.id}/schedules")
        status(response) must equal(OK)
        val list = contentAsJson(response).as[JsArray].value.map(_("id").as[Long])
        list must contain theSameElementsAs Seq(readableSchedule.id, writeableSchedule.id)
      }

      "reject updates on read-only and non-readable schedules" in {
        val readableSchedule = doSync(schedulesRepo.create(sampleSchedule(readableQuestionnaire.id, baseUser.id)))
        val hiddenSchedule = doSync(schedulesRepo.create(sampleSchedule(unrelatedQuestionnaire.id, baseUser.id)))

        val alteredSchedule = Some(Json.toJson(sampleSchedule(readableQuestionnaire.id, researchUser.id)))
        status(doAuthenticatedRequest(PUT, s"/v1/schedules/${hiddenSchedule.id}", alteredSchedule)) must equal(
          FORBIDDEN
        )
        status(doAuthenticatedRequest(PUT, s"/v1/schedules/${readableSchedule.id}", alteredSchedule)) must equal(
          FORBIDDEN
        )
      }

      "reject deletion on read-only and non-readable schedules" in {
        val readableSchedule = doSync(schedulesRepo.create(sampleSchedule(readableQuestionnaire.id, baseUser.id)))
        val hiddenSchedule = doSync(schedulesRepo.create(sampleSchedule(unrelatedQuestionnaire.id, baseUser.id)))

        val alteredSchedule = Some(Json.toJson(sampleSchedule(readableQuestionnaire.id, researchUser.id)))
        status(doAuthenticatedRequest(DELETE, s"/v1/schedules/${hiddenSchedule.id}", alteredSchedule)) must equal(
          FORBIDDEN
        )
        status(doAuthenticatedRequest(DELETE, s"/v1/schedules/${readableSchedule.id}", alteredSchedule)) must equal(
          FORBIDDEN
        )
      }
    }

    "logged in as admin" should {
      implicit val r = Role.Admin

      "grant read access to non-assigned questionnaires" in {
        val response = doAuthenticatedRequest(GET, s"/v1/questionnaires/${unrelatedQuestionnaire.id}/schedules")
        status(response) must equal(OK)
      }

      "grant write access to non-assigned questionnaires" in {
        val schedule =
          scheduleJson(unrelatedQuestionnaire.id, baseUser.id, "2018-10-20", "2018-11-05", "10:00:00", "23:45:07", 10)
        val creationResponse = doAuthenticatedRequest(POST, "/v1/schedules", Some(schedule))
        status(creationResponse) must equal(CREATED)
      }

      "grant write access to read-only questionnaires" in {
        val schedule =
          scheduleJson(readableQuestionnaire.id, baseUser.id, "2018-10-20", "2018-11-05", "10:00:00", "23:45:07", 10)
        val creationResponse = doAuthenticatedRequest(POST, "/v1/schedules", Some(schedule))
        status(creationResponse) must equal(CREATED)
      }
    }
  }

  def scheduleJson(
    questionnaireId: Long,
    userId: Long,
    startDate: String,
    endDate: String,
    startTime: String,
    endTime: String,
    frequency: Int
  ): JsValue = {
    Json.obj(
      "questionnaireId" -> questionnaireId,
      "userId" -> userId,
      "startDate" -> startDate,
      "endDate" -> endDate,
      "startTime" -> startTime,
      "endTime" -> endTime,
      "frequency" -> frequency
    )
  }

  private def sampleSchedule(questionnaireId: Long, userId: Long) = {
    Schedule(
      0,
      questionnaireId,
      userId,
      LocalDate.of(2000, 1, 1),
      LocalDate.of(2000, 1, 15),
      LocalTime.of(12, 0, 0, 0),
      LocalTime.of(20, 0, 0, 0),
      5
    )
  }
}
