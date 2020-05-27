package models

import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import models.AnswerType.AnswerType
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.libs.json.Writes
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile
import util.Serialization
import util.form.CustomForms._
import util.form.PlayFormsEnum.enum

case class Question(
  id: Long,
  content: String,
  answerType: AnswerType,
  answerLabels: Option[String] = None,
  answerRange: Option[String] = None,
  questionnaireId: Option[Long] = None
)

object Question {
  implicit val implicitWrites = new Writes[Question] {
    def writes(question: Question): JsValue = {
      val answerRangeJson = question.answerRange.map { rangeString =>
        if (question.answerType == AnswerType.RangeDiscrete)
          Json.toJson(Serialization.parseIntRange(rangeString))
        else
          Json.toJson(Serialization.parseFloatRange(rangeString))
      }
      Json.obj(
        "id" -> question.id,
        "content" -> question.content,
        "answerType" -> question.answerType,
        "answerLabels" -> question.answerLabels.map(Serialization.parseList),
        "answerRange" -> answerRangeJson,
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
  answerRange: Option[RangeFormData],
  questionnaireId: Option[Long]
)

case class RangeFormData(min: BigDecimal, max: BigDecimal)

object QuestionForm {
  val form = Form(
    mapping(
      "content" -> nonEmptyText,
      "answerType" -> enum(AnswerType),
      "answerLabels" -> emptyPreservingOptional(seq(text)),
      "answerRange" -> optional(mapping(
        "min" -> of(bigDecimalFormat),
        "max" -> of(bigDecimalFormat)
      )(RangeFormData.apply)(RangeFormData.unapply)),
      "questionnaireId" -> optional(longNumber)
    )(QuestionFormData.apply)(QuestionFormData.unapply)
  )
}

class QuestionTable(tag: Tag) extends Table[Question](tag, "question") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def content = column[String]("content")
  def answerType = column[AnswerType]("answer_type")
  def answerLabels = column[String]("answer_labels")
  def answerRange = column[String]("answer_range")
  def questionnaireId = column[Long]("questionnaire_id")

  override def * = (id, content, answerType, answerLabels.?, answerRange.?, questionnaireId.?) <> (Question.tupled, Question.unapply)
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
    val answerTypeCheck = validateAnswerType(question)

    val questionnaireCheck = question.questionnaireId.map { id =>
      dbConfig.db.run(questionnaires.filter(_.id === id).result).map(r => r.length).map {
        case 0 => throw new IllegalArgumentException(s"Referenced questionnaire with id $id does not exist")
        case _ =>
      }
    } getOrElse Future.unit

    Future.sequence(Seq(answerTypeCheck.map(_ => {}), questionnaireCheck)).map(_ => {})
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def validateAnswerType(question: Question): Future[Unit] = {
    Future {
      if (question.answerRange.isEmpty && question.answerType == AnswerType.RangeDiscrete)
        throw new IllegalArgumentException("Answer range for discrete-type question is missing")
      question.answerRange map { range =>
        question.answerType match {
          case AnswerType.Text =>
          case AnswerType.RangeDiscrete => Serialization.parseIntRange(range)
          case AnswerType.RangeContinuous => Serialization.parseFloatRange(range)
          case AnswerType.MultipleChoiceSingle =>
          case AnswerType.MultipleChoiceMany =>
          case AnswerType.TimeOfDay =>
          case AnswerType.Date =>
        }
      }

      def intRangePoints = question.answerRange.map(Serialization.parseIntRange).map(r => r.max - r.min + 1)

      question.answerLabels.map(Serialization.parseList) map { labels =>
        question.answerType match {
          case AnswerType.Text =>
          case AnswerType.RangeDiscrete if labels.length == intRangePoints.getOrElse(-1) =>
          case AnswerType.RangeContinuous if labels.length == 2 =>
          case AnswerType.MultipleChoiceSingle if labels.nonEmpty =>
          case AnswerType.MultipleChoiceMany if labels.nonEmpty =>
          case AnswerType.TimeOfDay =>
          case AnswerType.Date =>
          case _ => throw new IllegalArgumentException("Number of answer labels doesn't match answer type")
        }
      }
    }
  }
}
