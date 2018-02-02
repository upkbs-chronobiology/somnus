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
import slick.basic.DatabaseConfig
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile

// TODO: Add properties like type (numeric, multi-choice, text, ...), time of asking (morning or evening) etc.
// XXX: Maybe "text" instead of "content"?
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
  val questions = TableQuery[QuestionTable]
  // FIXME: Inject instead
  def dbConfig(): DatabaseConfig[JdbcProfile] = DatabaseConfigProvider.get[JdbcProfile](Play.current)

  def add(question: Question): Future[Question] = {
    dbConfig().db.run((questions returning questions.map(_.id)) += question) flatMap (id => {
      this.get(id).flatMap {
        case None => Future.failed(new IllegalStateException("Question could not be loaded after creation"))
        case Some(question) => Future.successful(question)
      }
    })
  }

  def delete(id: Long): Future[Int] = {
    dbConfig().db.run(questions.filter(_.id === id).delete)
  }

  def get(id: Long): Future[Option[Question]] = {
    dbConfig().db.run(questions.filter(_.id === id).result.headOption)
  }

  def listAll: Future[Seq[Question]] = {
    dbConfig().db.run(questions.result)
  }
}
