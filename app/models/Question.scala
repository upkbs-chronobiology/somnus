package models

import play.api.Play
import play.api.data.Form
import play.api.data.Forms._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.{JsValue, Json, Writes}

import scala.concurrent.Future
import slick.jdbc.JdbcProfile
import slick.jdbc.H2Profile.api._

import scala.concurrent.ExecutionContext.Implicits.global

case class Book(isbn: String)

// TODO: Add properties like type (numeric, multi-choice, text, ...), time of asking (morning or evening) etc.
case class Question(id: Long, content: String)

object Question {
  implicit val implicitWrites = new Writes[Question] {
    def writes(question: Question): JsValue = {
      Json.obj(
        "id" -> question.id,
        "content" -> question.content
      )
    }
  }

  val tupled = (this.apply _).tupled
}

case class QuestionFormData(content: String)

object QuestionForm {
  val form = Form(
    mapping(
      "content" -> nonEmptyText
    )(QuestionFormData.apply)(QuestionFormData.unapply)
  )
}

class QuestionTable(tag: Tag) extends Table[Question](tag, "question") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def content = column[String]("content")

  override def * = (id, content) <> (Question.tupled, Question.unapply)
}

object Questions {
  val dbConfig = DatabaseConfigProvider.get[JdbcProfile](Play.current)
  val questions = TableQuery[QuestionTable]

  def add(question: Question): Future[Question] = {
    dbConfig.db.run((questions returning questions.map(_.id)) += question) flatMap(idOptional => {
      this.get(idOptional).map(_.get)
    })
  }

  def delete(id: Long): Future[Int] = {
    dbConfig.db.run(questions.filter(_.id === id).delete)
  }

  def get(id: Long): Future[Option[Question]] = {
    dbConfig.db.run(questions.filter(_.id === id).result.headOption)
  }

  def listAll: Future[Seq[Question]] = {
    dbConfig.db.run(questions.result)
  }
}
