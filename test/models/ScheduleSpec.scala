package models

import java.time.LocalDate
import java.time.LocalTime

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Injecting
import testutil.Authenticated
import testutil.FreshDatabase
import testutil.TestUtils

class ScheduleSpec extends PlaySpec
  with GuiceOneAppPerSuite with Injecting with FreshDatabase with TestUtils with Authenticated {

  val schedules = inject[SchedulesRepository]
  val questionnaires = inject[QuestionnaireRepository]

  val questionnaire = doSync(questionnaires.create(Questionnaire(0, "Testionnaire", None)))

  "ScheduleRepository" should {
    "create, read, update, delete items" in {
      val schedule = Schedule(0, questionnaire.id, baseUser.id,
        LocalDate.of(2018, 4, 1), LocalDate.of(2018, 4, 7),
        LocalTime.of(8, 0), LocalTime.of(22, 30), 5)

      val createdSchedule = doSync(schedules.create(schedule))

      createdSchedule.id must be >= 0L
      createdSchedule.questionnaireId must equal(questionnaire.id)
      createdSchedule.userId must equal(baseUser.id)
      createdSchedule.startDate must equal(LocalDate.of(2018, 4, 1))
      createdSchedule.endDate must equal(LocalDate.of(2018, 4, 7))
      createdSchedule.startTime must equal(LocalTime.of(8, 0))
      createdSchedule.endTime must equal(LocalTime.of(22, 30))
      createdSchedule.frequency must equal(5)

      val readSchedule = doSync(schedules.get(createdSchedule.id)).get

      readSchedule must equal(createdSchedule)

      val secondSchedule = Schedule(createdSchedule.id, questionnaire.id, researchUser.id,
        LocalDate.of(2018, 4, 1), LocalDate.of(2018, 4, 20),
        LocalTime.of(8, 30), LocalTime.of(22, 30), 5)

      val updatedSchedule = doSync(schedules.update(secondSchedule))

      updatedSchedule.id must equal(createdSchedule.id)
      updatedSchedule.questionnaireId must equal(questionnaire.id)
      updatedSchedule.userId must equal(researchUser.id)
      updatedSchedule.startDate must equal(LocalDate.of(2018, 4, 1))
      updatedSchedule.endDate must equal(LocalDate.of(2018, 4, 20))
      updatedSchedule.startTime must equal(LocalTime.of(8, 30))
      updatedSchedule.endTime must equal(LocalTime.of(22, 30))
      updatedSchedule.frequency must equal(5)

      doSync(schedules.delete(createdSchedule.id))

      doSync(schedules.get(createdSchedule.id)) must equal(None)
    }

    "reject schedules with bad questionnaire ids" in {
      val schedule = Schedule(0, 999, baseUser.id,
        LocalDate.of(2018, 4, 1), LocalDate.of(2018, 4, 7),
        LocalTime.of(8, 0), LocalTime.of(22, 30), 5)

      an[IllegalArgumentException] mustBe thrownBy {
        doSync(schedules.create(schedule))
      }
    }

    "reject schedules with bad user ids" in {
      val schedule = Schedule(0, questionnaire.id, 888,
        LocalDate.of(2018, 4, 1), LocalDate.of(2018, 4, 7),
        LocalTime.of(8, 0), LocalTime.of(22, 30), 5)

      an[IllegalArgumentException] mustBe thrownBy {
        doSync(schedules.create(schedule))
      }
    }
  }
}
