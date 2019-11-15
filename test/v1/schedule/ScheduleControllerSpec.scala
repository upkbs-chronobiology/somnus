package v1.schedule

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import auth.roles.Role
import models.Questionnaire
import models.QuestionnairesRepository
import models.SchedulesRepository
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.JsArray
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.Injecting
import testutil.Authenticated
import testutil.FreshDatabase
import testutil.TestUtils

class ScheduleControllerSpec extends PlaySpec
  with GuiceOneAppPerSuite with Injecting with FreshDatabase with TestUtils with Authenticated with BeforeAndAfterEach {

  private val questionnaire = doSync(inject[QuestionnairesRepository].create(Questionnaire(0, "Testionnaire", None)))

  override def afterEach(): Unit = {
    super.afterEach()

    // delete all schedules (for above questionnaire)
    val repo = inject[SchedulesRepository]
    val schedules = doSync(repo.getByQuestionnaire(questionnaire.id))
    doSync(Future.sequence(schedules.map(s => repo.delete(s.id))))
  }

  "ScheduleController" should {
    "create, update and delete items" in {
      implicit val _ = Role.Researcher

      val schedule = scheduleJson(questionnaire.id, baseUser.id,
        "2018-10-20", "2018-11-05", "10:00:00", "23:45:07", 10)
      val creationResponse = doAuthenticatedRequest(POST, "/v1/schedules", Some(schedule))

      status(creationResponse) must equal(CREATED)
      val createdSchedule = contentAsJson(creationResponse)
      val createdId = createdSchedule("id").as[Long]

      createdId must be >= 0L
      createdSchedule("questionnaireId").as[Long] must equal(questionnaire.id)
      createdSchedule("userId").as[Long] must equal(baseUser.id)
      createdSchedule("startDate").as[String] must equal("2018-10-20")
      createdSchedule("endDate").as[String] must equal("2018-11-05")
      createdSchedule("startTime").as[String] must equal("10:00:00")
      createdSchedule("endTime").as[String] must equal("23:45:07")
      createdSchedule("frequency").as[Int] must equal(10)

      val listAfterCreation = contentAsJson(doAuthenticatedRequest(GET, s"/v1/questionnaires/${questionnaire.id}/schedules")).as[JsArray].value
      listAfterCreation.length must equal(1)
      listAfterCreation(0)("id").as[Long] must equal(createdId)

      val secondSchedule = scheduleJson(questionnaire.id, baseUser.id,
        "2019-10-20", "2019-11-05", "10:00:00", "23:45:07", 20)
      val updateResponse = doAuthenticatedRequest(PUT, s"/v1/schedules/$createdId", Some(secondSchedule))

      status(updateResponse) must equal(OK)
      val updatedSchedule = contentAsJson(updateResponse)
      updatedSchedule("id").as[Long] must be >= 0L
      updatedSchedule("questionnaireId").as[Long] must equal(questionnaire.id)
      updatedSchedule("userId").as[Long] must equal(baseUser.id)
      updatedSchedule("startDate").as[String] must equal("2019-10-20")
      updatedSchedule("endDate").as[String] must equal("2019-11-05")
      updatedSchedule("startTime").as[String] must equal("10:00:00")
      updatedSchedule("endTime").as[String] must equal("23:45:07")
      updatedSchedule("frequency").as[Int] must equal(20)

      val deleteResponse = doAuthenticatedRequest(DELETE, s"/v1/schedules/$createdId")

      status(deleteResponse) must equal(OK)

      val listAfterDeletion = contentAsJson(doAuthenticatedRequest(GET, s"/v1/questionnaires/${questionnaire.id}/schedules")).as[JsArray].value
      listAfterDeletion.length must equal(0)
    }

    "reject adding if already present" in {
      implicit val _ = Role.Researcher

      val schedule = scheduleJson(questionnaire.id, baseUser.id,
        "2018-10-20", "2018-11-05", "10:00:00", "23:45:07", 10)
      val creationResponse = doAuthenticatedRequest(POST, "/v1/schedules", Some(schedule))
      status(creationResponse) must equal(CREATED)

      val reSchedule = scheduleJson(questionnaire.id, baseUser.id,
        "2019-11-15", "2019-12-20", "08:00:00", "11:22:42", 66)
      val reCreationResponse = doAuthenticatedRequest(POST, "/v1/schedules", Some(reSchedule))
      status(reCreationResponse) must equal(BAD_REQUEST)
    }

    "only create 1 schedule in case of many repeating requests" in {
      implicit val _ = Role.Researcher

      val schedule = scheduleJson(questionnaire.id, baseUser.id,
        "2018-10-20", "2018-11-05", "10:00:00", "23:45:07", 10)
      val createdCount = Future.sequence(List.fill(10)(doAuthenticatedRequest(POST, "/v1/schedules", Some(schedule))))
        .map(resultList => (resultList.toStream.map(r => r.header.status) map {
          case CREATED => 1
          case _ => 0
        }).sum)
      doSync(createdCount) must equal(1)

      val schedules = doSync(inject[SchedulesRepository].getByQuestionnaire(questionnaire.id))
      schedules.length must equal(1)
    }

    "list my schedules" in {
      val listBeforeCreation = contentAsJson(doAuthenticatedRequest(GET, s"/v1/users/me/schedules")).as[JsArray].value
      listBeforeCreation.length must equal(0)

      val schedule = scheduleJson(questionnaire.id, baseUser.id,
        "2019-10-20", "2019-11-05", "10:00:00", "23:45:07", 10)
      status(doAuthenticatedRequest(POST, "/v1/schedules", Some(schedule), role = Some(Role.Researcher))) must equal(CREATED)

      val listAfterCreation = contentAsJson(doAuthenticatedRequest(GET, s"/v1/users/me/schedules")).as[JsArray].value
      listAfterCreation.length must equal(1)
      listAfterCreation(0)("userId").as[Long] must equal(baseUser.id)
    }

    "reject badly formatted dates" in {
      implicit val _ = Role.Researcher
      val schedule = scheduleJson(questionnaire.id, baseUser.id,
        "10/20/2018", "5/11/2018", "10:00:00", "23:45:07", 10)
      status(doAuthenticatedRequest(POST, "/v1/schedules", Some(schedule))) must equal(BAD_REQUEST)
    }

    "reject badly formatted times" in {
      implicit val _ = Role.Researcher
      val schedule = scheduleJson(questionnaire.id, baseUser.id,
        "2019-10-20", "2019-11-05", "10.00", "23.45", 10)
      status(doAuthenticatedRequest(POST, "/v1/schedules", Some(schedule))) must equal(BAD_REQUEST)
    }
  }

  def scheduleJson(questionnaireId: Long, userId: Long, startDate: String, endDate: String,
    startTime: String, endTime: String, frequency: Int): JsValue = {
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
}
