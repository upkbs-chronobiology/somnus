package models

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import javax.inject.Inject
import javax.inject.Singleton
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
import util.Serialization

case class Question(
  id: Long,
  content: String,
  answerType: AnswerType,
  answerLabels: Option[String] = None,
  questionnaireId: Option[Long] = None
)

object Question {
  implicit val implicitWrites = new Writes[Question] {
    def writes(question: Question): JsValue = {
      Json.obj(
        "id" -> question.id,
        "content" -> question.content,
        "answerType" -> question.answerType,
        "answerLabels" -> question.answerLabels.map(Serialization.parseList),
        "questionnaireId" -> question.questionnaireId
      )
    }
  }

  val tupled = (this.apply _).tupled
}

case class QuestionFormData(
  content: String,
  answerType: AnswerType,
  answerLabels: Option[Seq[String]],
  questionnaireId: Option[Long]
)

object QuestionForm {
  val form = Form(
    mapping(
      "content" -> nonEmptyText,
      "answerType" -> enum(AnswerType),
      "answerLabels" -> optional(seq(text)),
      "questionnaireId" -> optional(longNumber)
    )(QuestionFormData.apply)(QuestionFormData.unapply)
  )
}

class QuestionTable(tag: Tag) extends Table[Question](tag, "question") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def content = column[String]("content")
  def answerType = column[AnswerType]("answer_type")
  def answerLabels = column[String]("answer_labels")
  def questionnaireId = column[Long]("questionnaire_id")

  override def * = (id, content, answerType, answerLabels.?, questionnaireId.?) <> (Question.tupled, Question.unapply)
}

@Singleton
class QuestionsRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, answersRepo: AnswersRepository) {

  private def questions = TableQuery[QuestionTable]
  private def questionnaires = TableQuery[QuestionnaireTable]

  private def dbConfig = dbConfigProvider.get[JdbcProfile]

  def add(question: Question): Future[Question] = {
    validate(question) flatMap { _ =>
      dbConfig.db.run((questions returning questions.map(_.id)) += question) flatMap (id => {
        this.get(id).flatMap {
          case None => Future.failed(new IllegalStateException("Question could not be loaded after creation"))
          case Some(q) => Future.successful(q)
        }
      })
    }
  }

  def update(question: Question): Future[Question] = {
    validate(question) flatMap { _ =>
      val query = questions.filter(_.id === question.id).update(question)
      dbConfig.db.run(query).flatMap(_ =>
        this.get(question.id).map(_.getOrElse(throw new IllegalStateException("Question not found after update")))
      )
    }
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

  private def validate(question: Question): Future[Unit] = {
    val answerTypeCheck = Future {
      question.answerLabels.map(Serialization.parseList) map { labels =>
        question.answerType match {
          case AnswerType.Text =>
          case AnswerType.RangeDiscrete5 if labels.length == 5 =>
          case AnswerType.RangeContinuous if labels.length == 2 =>
          case _ => throw new IllegalArgumentException("Number of answer labels doesn't match answer type")
        }
      }
    }

    val questionnaireCheck = question.questionnaireId.map { id =>
      dbConfig.db.run(questionnaires.filter(_.id === id).result).map(r => r.length).map {
        case 0 => throw new IllegalArgumentException(s"Referenced questionnaire with id $id does not exist")
        case _ =>
      }
    } getOrElse Future.unit

    Future.sequence(Seq(answerTypeCheck.map(_ => {}), questionnaireCheck)).map(_ => {})
  }
}
