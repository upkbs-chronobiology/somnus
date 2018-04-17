package models

import java.time.LocalDate
import java.time.LocalTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import javax.inject.Inject
import javax.inject.Singleton
import play.api.data.Form
import play.api.data.Forms._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Writes
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile
import util.TemporalSqlMappings

case class Schedule(
  id: Long,
  questionnaireId: Long, userId: Long,
  startDate: LocalDate, endDate: LocalDate,
  startTime: LocalTime, endTime: LocalTime,
  frequency: Int
)

object Schedule {
  implicit val writes = new Writes[Schedule] {
    def writes(schedule: Schedule): JsValue = {
      Json.obj(
        "id" -> schedule.id,
        "questionnaireId" -> schedule.questionnaireId,
        "userId" -> schedule.userId,
        "startDate" -> schedule.startDate,
        "endDate" -> schedule.endDate,
        "startTime" -> schedule.startTime,
        "endTime" -> schedule.endTime,
        "frequency" -> schedule.frequency
      )
    }
  }

  val tupled = (this.apply _).tupled
}

case class ScheduleFormData(
  questionnaireId: Long, userId: Long,
  startDate: LocalDate, endDate: LocalDate,
  startTime: LocalTime, endTime: LocalTime,
  frequency: Int
)

object ScheduleForm {
  val form = Form(
    mapping(
      "questionnaireId" -> longNumber,
      "userId" -> longNumber,
      "startDate" -> localDate,
      "endDate" -> localDate,
      "startTime" -> localTime,
      "endTime" -> localTime,
      "frequency" -> number
    )(ScheduleFormData.apply)(ScheduleFormData.unapply)
  )
}

class ScheduleTable(tag: Tag) extends Table[Schedule](tag, "schedule") with TemporalSqlMappings {
  val id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  val questionnaireId = column[Long]("questionnaire_id")
  val userId = column[Long]("user_id")
  val startDate = column[LocalDate]("start_date")
  val endDate = column[LocalDate]("end_date")
  val startTime = column[LocalTime]("start_time")
  val endTime = column[LocalTime]("end_time")
  val frequency = column[Int]("frequency")

  val questionnaire = foreignKey("questionnaire", questionnaireId, TableQuery[QuestionnaireTable])(_.id)
  val user = foreignKey("user", userId, TableQuery[UserTable])(_.id)

  override def * =
    (id, questionnaireId, userId, startDate, endDate, startTime, endTime, frequency) <>
      (Schedule.tupled, Schedule.unapply)
}

@Singleton
class SchedulesRepository @Inject()(dbConfigProvider: DatabaseConfigProvider) {

  def schedules = TableQuery[ScheduleTable]
  def questionnaires = TableQuery[QuestionnaireTable]
  def users = TableQuery[UserTable]

  def dbConfig = dbConfigProvider.get[JdbcProfile]

  def get(id: Long): Future[Option[Schedule]] = {
    dbConfig.db.run(schedules.filter(_.id === id).result.headOption)
  }

  def getByQuestionnaire(questionnaireId: Long): Future[Seq[Schedule]] = {
    dbConfig.db.run(schedules.filter(_.questionnaireId === questionnaireId).result)
  }

  def getByUser(userId: Long): Future[Seq[Schedule]] = {
    dbConfig.db.run(schedules.filter(_.userId === userId).result)
  }

  def create(schedule: Schedule): Future[Schedule] = {
    validate(schedule) flatMap { _ =>
      dbConfig.db.run((schedules returning schedules.map(_.id)) += schedule)
        .flatMap(this.get(_).map(_.getOrElse(throw new IllegalStateException("Failed to load just created schedule"))))
    }
  }

  def update(schedule: Schedule): Future[Schedule] = {
    validate(schedule) flatMap { _ =>
      dbConfig.db.run(schedules.filter(_.id === schedule.id).update(schedule)).flatMap {
        case 1 => this.get(schedule.id)
          .map(_.getOrElse(throw new IllegalStateException("Failed to load just created schedule")))
        case 0 => throw new IllegalArgumentException(s"Failed to update schedule with id ${schedule.id}")
      }
    }
  }

  def delete(id: Long): Future[Int] = {
    dbConfig.db.run(schedules.filter(_.id === id).delete)
  }

  def validate(schedule: Schedule): Future[Unit] = {
    val questionnaireCheck = dbConfig.db.run(questionnaires.filter(_.id === schedule.questionnaireId).result.headOption) map {
      case Some(_) =>
      case None => throw new IllegalArgumentException(s"Questionnaire with id ${schedule.questionnaireId} does not exist")
    }
    val userCheck = dbConfig.db.run(users.filter(_.id === schedule.userId).result.headOption) map {
      case Some(_) =>
      case None => throw new IllegalArgumentException(s"User with id ${schedule.userId} does not exist")
    }

    Future.sequence(Seq(questionnaireCheck, userCheck)).map(_ => Future.unit)
  }
}
