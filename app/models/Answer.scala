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
          val range = Serialization.parseFloatRange(
            question.answerRange.getOrElse(throw new IllegalArgumentException("Range is missing on continuous-range question")))
          val value = answer.content.toDouble
          if (value < range.min || value > range.max)
            throw new IllegalArgumentException(s"Bad number format - expected real number ${range.min} <= x <= ${range.max}")
        case AnswerType.RangeDiscrete =>
          val range = Serialization.parseIntRange(
            question.answerRange.getOrElse(throw new IllegalArgumentException("Range is missing on discrete-range question")))
          val value = answer.content.toLong
          if (value < range.min || value > range.max)
            throw new IllegalArgumentException(s"Bad number format - expected natural number ${range.min} <= x <= ${range.max}")
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
