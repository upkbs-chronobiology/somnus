package models

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

// XXX: Should content be of type String? It may depend on the question type (number, date, choice, ...)
case class Answer(id: Long, questionId: Long, content: String)

object Answer {
  implicit val implicitWrites = new Writes[Answer] {
    def writes(answer: Answer): JsValue = {
      Json.obj(
        "id" -> answer.id,
        "question_id" -> answer.questionId,
        "content" -> answer.content
      )
    }
  }

  val tupled = (this.apply _).tupled
}

case class AnswerFormData(questionId: Long, content: String)

object AnswerForm {
  val form = Form(
    mapping(
      "question_id" -> longNumber, // XXX: Can we map to existing question ids?
      "content" -> nonEmptyText
    )(AnswerFormData.apply)(AnswerFormData.unapply)
  )
}

class AnswerTable(tag: Tag) extends Table[Answer](tag, "answer") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def questionId = column[Long]("question_id")
  def content = column[String]("content")
  def question = foreignKey("question", questionId, Questions.questions)(_.id)

  override def * = (id, questionId, content) <> (Answer.tupled, Answer.unapply)
}

object Answers {
  def dbConfig() = DatabaseConfigProvider.get[JdbcProfile](Play.current)
  val answers = TableQuery[AnswerTable]

  def add(answer: Answer): Future[Answer] = {
    dbConfig().db.run((answers returning answers.map(_.id)) += answer)
      // XXX: Why not just .map(_.get) on the Option?
      // https://softwareengineering.stackexchange.com/questions/365089/is-using-optionget-really-a-bad-idea-here
      .flatMap(this.get(_).flatMap {
        case Some(a) => Future.successful(a)
        case None => Future.failed(new IllegalStateException("Failed to load answer after creation"))
      })
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
