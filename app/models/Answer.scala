package models

import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.data.Form
import play.api.data.Forms._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.libs.json.Writes
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._
import slick.sql.SqlProfile.ColumnOption.SqlType
import util.Serialization
import util.TemporalSqlMappings
import util.form.FormOffsetDateTime.offsetDateTime

case class Answer(
  id: Long,
  questionId: Long,
  content: String,
  userId: Long,
  created: Timestamp,
  createdLocal: OffsetDateTime
)

object Answer {
  implicit val implicitWrites = new Writes[Answer] {
    def writes(answer: Answer): JsValue = {
      Json.obj(
        "id" -> answer.id,
        "questionId" -> answer.questionId,
        "content" -> answer.content,
        "userId" -> answer.userId,
        "created" -> answer.created,
        "createdLocal" -> answer.createdLocal.toString
      )
    }
  }

  val tupled = (this.apply _).tupled
}

case class AnswerFormData(questionId: Long, content: String, userId: Option[Long], createdLocal: OffsetDateTime)

object AnswerForm {
  val form = Form(
    mapping(
      "questionId" -> longNumber, // XXX: Can we map to existing question ids?
      "content" -> nonEmptyText,
      "userId" -> optional(longNumber),
      "createdLocal" -> offsetDateTime
    )(AnswerFormData.apply)(AnswerFormData.unapply)
  )
}

class AnswerTable(tag: Tag) extends Table[Answer](tag, "answer") with TemporalSqlMappings {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def questionId = column[Long]("question_id")
  def content = column[String]("content")
  def userId = column[Long]("user_id")
  // XXX: O.AutoInc is a hack to let Slick ignore it on write ops (because map doesn't seem to work with mapped columns)
  // https://stackoverflow.com/questions/50386823/mapping-a-tablequery-to-an-offsetdatetime-column
  def created = column[Timestamp]("created", SqlType("TIMESTAMP NOT NULL DEFAULT current_timestamp()"), O.AutoInc)
  def createdLocal = column[OffsetDateTime]("created_local")

  def question = foreignKey("question", questionId, TableQuery[QuestionTable])(_.id)
  def user = foreignKey("user", userId, TableQuery[UserTable])(_.id)

  override def * = (id, questionId, content, userId, created, createdLocal) <> (Answer.tupled, Answer.unapply)
}

@Singleton
class AnswersRepository @Inject() (dbConfigProvider: DatabaseConfigProvider) {

  def dbConfig() = dbConfigProvider.get[MySQLProfile]

  val answers = TableQuery[AnswerTable]
  val questions = TableQuery[QuestionTable]

  // required because we don't want to insert "created", but use its default value
  // XXX: Currently unused because of AutoInc hack (see below)
  //  type InsertTuple = (Long, String, Long, OffsetDateTime)
  //  type RepInsertTuple = (Rep[Long], Rep[String], Rep[Long], Rep[OffsetDateTime])
  //  private def mapToInsertColumns(answers: TableQuery[AnswerTable]): QueryBase[Seq[(Long, String, Long, OffsetDateTime)]] =
  //    answers.map[RepInsertTuple, Seq[InsertTuple], InsertTuple]((table: AnswerTable) =>
  //      (table.questionId, table.content, table.userId, table.createdLocal))
  //  private val InsertValuesMap = (answer: Answer) => (answer.questionId, answer.content, answer.userId, answer.createdLocal)

  /** Make sure answer content corresponds with answerType in question.
    */
  private def assureTypeConsistency(answer: Answer): Future[Unit] = {
    val query = questions.filter(_.id === answer.questionId).result.headOption
    dbConfig().db.run(query).map {
      case None => throw new IllegalArgumentException("Question id for answer not found")
      case Some(question) =>
        question.answerType match {
          case AnswerType.RangeContinuous =>
            val range = Serialization.parseFloatRange(
              question.answerRange
                .getOrElse(throw new IllegalArgumentException("Range is missing on continuous-range question"))
            )
            val value = answer.content.toDouble
            if (value < range.min || value > range.max)
              throw new IllegalArgumentException(
                s"Bad number format - expected real number ${range.min} <= x <= ${range.max}"
              )
          case AnswerType.RangeDiscrete =>
            val range = Serialization.parseIntRange(
              question.answerRange
                .getOrElse(throw new IllegalArgumentException("Range is missing on discrete-range question"))
            )
            val value = answer.content.toLong
            if (value < range.min || value > range.max)
              throw new IllegalArgumentException(
                s"Bad number format - expected natural number ${range.min} <= x <= ${range.max}"
              )
          case AnswerType.MultipleChoiceSingle =>
            val value = answer.content.toLong
            val numOptions = question.answerLabels.map(Serialization.parseList(_).length).getOrElse(-1)
            if (value < 0 || value >= numOptions)
              throw new IllegalArgumentException("Answer option index doesn't match answer options")
          case AnswerType.MultipleChoiceMany =>
            val numOptions = question.answerLabels.map(Serialization.parseList(_).length).getOrElse(-1)
            val values = Serialization.parseList(answer.content).map(_.toLong)
            if (values.distinct.length < values.length)
              throw new IllegalArgumentException("There are duplicate answer option indices")
            values foreach { value =>
              if (value < 0 || value >= numOptions)
                throw new IllegalArgumentException("At least one answer option index doesn't match answer options")
            }
          case AnswerType.TimeOfDay =>
            try {
              LocalTime.parse(answer.content)
            } catch {
              case e: DateTimeParseException => throw new IllegalArgumentException(e)
            }
          case AnswerType.Date =>
            try {
              LocalDate.parse(answer.content)
            } catch {
              case e: DateTimeParseException => throw new IllegalArgumentException(e)
            }
          case _ => Unit
        }
    }
  }

  def add(answer: Answer): Future[Answer] = {
    // XXX: See above (AutoInc hack)
    assureTypeConsistency(answer).flatMap { _ =>
      dbConfig().db
        .run((answers returning answers.map(_.id)) += answer)
        .flatMap(this.get(_).flatMap {
          case Some(a) => Future.successful(a)
          case None => Future.failed(new IllegalStateException("Failed to load answer after creation"))
        })
    }
    //    assureTypeConsistency(answer).flatMap { _ =>
    //      dbConfig().db.run((mapToInsertColumns(answers) returning answers.map(_.id)) += InsertValuesMap(answer))
    //        .flatMap(this.get(_).flatMap {
    //          case Some(a) => Future.successful(a)
    //          case None => Future.failed(new IllegalStateException("Failed to load answer after creation"))
    //        })
    //    }
  }

  def addAll(newAnswers: Seq[Answer]): Future[Seq[Answer]] = {
    // XXX: See above (AutoInc hack)
    Future.sequence(newAnswers.map(answer => assureTypeConsistency(answer))).flatMap { _ =>
      dbConfig().db
        .run(((answers returning answers.map(_.id)) ++= newAnswers).transactionally)
        .flatMap(
          createdSeq =>
            Future.sequence(createdSeq.map(this.get(_).map {
              case Some(a) => a
              case None => throw new IllegalStateException("Failed to load an answer after creation")
            }))
        )
    }
    //    Future.sequence(newAnswers.map(answer => assureTypeConsistency(answer))).flatMap { _ =>
    //      dbConfig().db.run(((mapToInsertColumns(answers) returning answers.map(_.id)) ++= newAnswers.map(InsertValuesMap)).transactionally)
    //        .flatMap(createdSeq => Future.sequence(createdSeq.map(this.get(_).map {
    //          case Some(a) => a
    //          case None => throw new IllegalStateException("Failed to load an answer after creation")
    //        })))
    //    }
  }

  def delete(id: Long): Future[Int] = {
    dbConfig().db.run(answers.filter(_.id === id).delete)
  }

  def get(id: Long): Future[Option[Answer]] = {
    dbConfig().db.run(answers.filter(_.id === id).result.headOption)
  }

  def getByQuestion(questionId: Long): Future[Seq[Answer]] = {
    dbConfig().db.run(answers.filter(_.questionId === questionId).result)
  }

  def listAll(): Future[Seq[Answer]] = {
    dbConfig().db.run(answers.result)
  }

  def listByQuestionnaire(questionnaireId: Long): Future[Seq[Answer]] = {
    val filteredQuestions = questions.filter(_.questionnaireId === questionnaireId)
    val query = answers join filteredQuestions on (_.questionId === _.id) map (_._1)
    dbConfig().db.run(query.result)
  }

  def listByUserAndQuestionnaire(userId: Long, questionnaireId: Long): Future[Seq[Answer]] = {
    val filteredAnswers = answers.filter(_.userId === userId)
    val filteredQuestions = questions.filter(_.questionnaireId === questionnaireId)
    val query = filteredAnswers join filteredQuestions on (_.questionId === _.id) map (_._1)
    dbConfig().db.run(query.result)
  }
}
