package models

import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import models.AnswerType.AnswerType
import play.api.data.Form
import play.api.data.Forms._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Writes
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile
import util.PlayFormsEnum.enum

// TODO: Add property like time of asking (morning or evening)
case class Question(id: Long, content: String, answerType: AnswerType, questionnaireId: Option[Long] = None)

object Question {
  implicit val implicitWrites = new Writes[Question] {
    def writes(question: Question): JsValue = {
      Json.obj(
        "id" -> question.id,
        "content" -> question.content,
        "answerType" -> question.answerType,
        "questionnaireId" -> question.questionnaireId
      )
    }
  }

  val tupled = (this.apply _).tupled
}

case class QuestionFormData(content: String, answerType: AnswerType, questionnaireId: Option[Long])

object QuestionForm {
  val form = Form(
    mapping(
      "content" -> nonEmptyText,
      "answerType" -> enum(AnswerType),
      "questionnaireId" -> optional(longNumber)
    )(QuestionFormData.apply)(QuestionFormData.unapply)
  )
}

class QuestionTable(tag: Tag) extends Table[Question](tag, "question") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def content = column[String]("content")
  def answerType = column[AnswerType]("answer_type")
  def questionnaireId = column[Long]("questionnaire_id")

  override def * = (id, content, answerType, questionnaireId.?) <> (Question.tupled, Question.unapply)
}

@Singleton
class QuestionsRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, answersRepo: AnswersRepository) {

  private def questions = TableQuery[QuestionTable]

  private def dbConfig = dbConfigProvider.get[JdbcProfile]

  def add(question: Question): Future[Question] = {
    dbConfig.db.run((questions returning questions.map(_.id)) += question) flatMap (id => {
      this.get(id).flatMap {
        case None => Future.failed(new IllegalStateException("Question could not be loaded after creation"))
        case Some(q) => Future.successful(q)
      }
    })
  }

  def update(question: Question): Future[Question] = {
    val query = questions.filter(_.id === question.id).update(question)
    dbConfig.db.run(query).flatMap(_ =>
      this.get(question.id).map(_.getOrElse(throw new IllegalStateException("Question not found after update")))
    )
  }

  def delete(id: Long): Future[Int] = {
    answersRepo.getByQuestion(id).flatMap {
      case answers if answers.nonEmpty =>
        Future.failed(new IllegalArgumentException("Already answered questions cannot be deleted"))
      case _ => dbConfig.db.run(questions.filter(_.id === id).delete)
    }
  }

  def get(id: Long): Future[Option[Question]] = {
    dbConfig.db.run(questions.filter(_.id === id).result.headOption)
  }

  def listAll: Future[Seq[Question]] = {
    dbConfig.db.run(questions.result)
  }

  def listByQuestionnaire(questionnaireId: Long): Future[Seq[Question]] = {
    dbConfig.db.run(questions.filter(_.questionnaireId === questionnaireId).result)
  }
}
