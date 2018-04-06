package models

import java.sql.Timestamp

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
import slick.sql.SqlProfile.ColumnOption.SqlType
import util.Serialization

// XXX: Should content be of type String? It may depend on the question type (number, date, choice, ...)
case class Answer(id: Long, questionId: Long, content: String, userId: Long, created: Timestamp)

object Answer {
  implicit val implicitWrites = new Writes[Answer] {
    def writes(answer: Answer): JsValue = {
      Json.obj(
        "id" -> answer.id,
        "questionId" -> answer.questionId,
        "content" -> answer.content,
        "userId" -> answer.userId,
        "created" -> answer.created
      )
    }
  }

  val tupled = (this.apply _).tupled
}

case class AnswerFormData(questionId: Long, content: String, userId: Option[Long])

object AnswerForm {
  val form = Form(
    mapping(
      "questionId" -> longNumber, // XXX: Can we map to existing question ids?
      "content" -> nonEmptyText,
      "userId" -> optional(longNumber)
    )(AnswerFormData.apply)(AnswerFormData.unapply)
  )
}

class AnswerTable(tag: Tag) extends Table[Answer](tag, "answer") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def questionId = column[Long]("question_id")
  def content = column[String]("content")
  def userId = column[Long]("user_id")
  def created = column[Timestamp]("created", SqlType("TIMESTAMP NOT NULL DEFAULT current_timestamp()"))

  def question = foreignKey("question", questionId, TableQuery[QuestionTable])(_.id)
  def user = foreignKey("user", userId, TableQuery[UserTable])(_.id)

  override def * = (id, questionId, content, userId, created) <> (Answer.tupled, Answer.unapply)
}

@Singleton
class AnswersRepository @Inject()(dbConfigProvider: DatabaseConfigProvider) {

  def dbConfig() = dbConfigProvider.get[JdbcProfile]

  val answers = TableQuery[AnswerTable]
  val questions = TableQuery[QuestionTable]

  // required because we don't want to insert "created", but use its default value
  private val InsertColumnsMap = (table: AnswerTable) => (table.questionId, table.content, table.userId)
  private val InsertValuesMap = (answer: Answer) => (answer.questionId, answer.content, answer.userId)

  /** Make sure answer content corresponds with answerType in question.
    */
  private def assureTypeConsistency(answer: Answer): Future[Unit] = {
    val query = questions.filter(_.id === answer.questionId).result.headOption
    dbConfig().db.run(query).map {
      case None => throw new IllegalArgumentException("Question id for answer not found")
      case Some(question) => question.answerType match {
        case AnswerType.RangeContinuous =>
          val value = answer.content.toDouble
          if (value < 0 || value > 1) throw new IllegalArgumentException("Bad number format - expected real number 0 <= x <= 1")
        case AnswerType.RangeDiscrete =>
          val value = answer.content.toLong
          if (value < 1 || value > 5) throw new IllegalArgumentException("Bad number format - expected natural number 1 <= x <= 5")
        case AnswerType.MultipleChoice =>
          val value = answer.content.toLong
          val numOptions = question.answerLabels.map(Serialization.parseList(_).length).getOrElse(-1)
          if (value < 0 || value >= numOptions) throw new IllegalArgumentException("Answer option index doesn't match answer options")
        case _ => Unit
      }
    }
  }

  def add(answer: Answer): Future[Answer] = {
    assureTypeConsistency(answer).flatMap { _ =>
      dbConfig().db.run((answers.map(InsertColumnsMap) returning answers.map(_.id)) += InsertValuesMap(answer))
        .flatMap(this.get(_).flatMap {
          case Some(a) => Future.successful(a)
          case None => Future.failed(new IllegalStateException("Failed to load answer after creation"))
        })
    }
  }

  def addAll(newAnswers: Seq[Answer]): Future[Seq[Answer]] = {
    Future.sequence(newAnswers.map(answer => assureTypeConsistency(answer))).flatMap { _ =>
      dbConfig().db.run(((answers.map(InsertColumnsMap) returning answers.map(_.id)) ++= newAnswers.map(InsertValuesMap)).transactionally)
        .flatMap(createdSeq => Future.sequence(createdSeq.map(this.get(_).map {
          case Some(a) => a
          case None => throw new IllegalStateException("Failed to load an answer after creation")
        })))
    }
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
}
