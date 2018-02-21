package models

import java.sql.Timestamp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.Play
import play.api.data.Form
import play.api.data.Forms._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Writes
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile
import slick.sql.SqlProfile.ColumnOption.SqlType

// XXX: Should content be of type String? It may depend on the question type (number, date, choice, ...)
case class Answer(id: Long, questionId: Long, content: String, userId: Long, created: Timestamp)

object Answer {
  implicit val implicitWrites = new Writes[Answer] {
    def writes(answer: Answer): JsValue = {
      Json.obj(
        "id" -> answer.id,
        "question_id" -> answer.questionId,
        "content" -> answer.content,
        "user_id" -> answer.userId,
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

  def question = foreignKey("question", questionId, Questions.questions)(_.id)
  def user = foreignKey("user", userId, TableQuery[UserTable])(_.id)

  override def * = (id, questionId, content, userId, created) <> (Answer.tupled, Answer.unapply)
}

object Answers {
  def dbConfig() = DatabaseConfigProvider.get[JdbcProfile](Play.current)
  val answers = TableQuery[AnswerTable]

  // required because we don't want to insert "created", but use its default value
  private val InsertColumnsMap = (table: AnswerTable) => (table.questionId, table.content, table.userId)
  private val InsertValuesMap = (answer: Answer) => (answer.questionId, answer.content, answer.userId)

  def add(answer: Answer): Future[Answer] = {
    dbConfig().db.run((answers.map(InsertColumnsMap) returning answers.map(_.id)) += InsertValuesMap(answer))
      .flatMap(this.get(_).flatMap {
        case Some(a) => Future.successful(a)
        case None => Future.failed(new IllegalStateException("Failed to load answer after creation"))
      })
  }

  def addAll(newAnswers: Seq[Answer]): Future[Seq[Answer]] = {
    dbConfig().db.run(((answers.map(InsertColumnsMap) returning answers.map(_.id)) ++= newAnswers.map(InsertValuesMap)).transactionally)
      .flatMap(createdSeq => Future.sequence(createdSeq.map(this.get(_).map {
        case Some(a) => a
        case None => throw new IllegalStateException("Failed to load an answer after creation")
      })))
  }

  def delete(id: Long): Future[Int] = {
    dbConfig().db.run(answers.filter(_.id === id).delete)
  }

  def get(id: Long): Future[Option[Answer]] = {
    dbConfig().db.run(answers.filter(_.id === id).result.headOption)
  }

  def listAll(): Future[Seq[Answer]] = {
    dbConfig().db.run(answers.result)
  }
}
