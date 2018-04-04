package models

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
import slick.lifted.Tag

case class Questionnaire(id: Long, name: String, studyId: Option[Long])

object Questionnaire {
  implicit val implicitWrites = new Writes[Questionnaire] {
    def writes(questionnaire: Questionnaire): JsValue = {
      Json.obj(
        "id" -> questionnaire.id,
        "name" -> questionnaire.name,
        "studyId" -> questionnaire.studyId
      )
    }
  }

  val tupled = (this.apply _).tupled
}

case class QuestionnaireFormData(name: String, studyId: Option[Long])

object QuestionnaireForm {
  val form = Form(
    mapping(
      "name" -> nonEmptyText,
      "studyId" -> optional(longNumber)
    )(QuestionnaireFormData.apply)(QuestionnaireFormData.unapply)
  )
}

class QuestionnaireTable(tag: Tag) extends Table[Questionnaire](tag, "questionnaire") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name")
  def studyId = column[Long]("study_id")

  override def * = (id, name, studyId.?) <> (Questionnaire.tupled, Questionnaire.unapply)
}

@Singleton
class QuestionnaireRepository @Inject()(dbConfigProvider: DatabaseConfigProvider) {

  private def questionnaires = TableQuery[QuestionnaireTable]
  private def questions = TableQuery[QuestionTable]

  private def dbConfig() = dbConfigProvider.get[JdbcProfile]

  def listAll(): Future[Seq[Questionnaire]] = {
    dbConfig().db.run(questionnaires.result)
  }

  def read(id: Long): Future[Option[Questionnaire]] = {
    dbConfig().db.run(questionnaires.filter(_.id === id).result.headOption)
  }

  def create(questionnaire: Questionnaire): Future[Questionnaire] = {
    val query = (questionnaires returning questionnaires.map(_.id)) += questionnaire
    dbConfig().db.run(query).flatMap(this.read(_)
      .map(_.getOrElse(throw new IllegalStateException("Failed to load questionnaire after creation"))))
  }

  def update(questionnaire: Questionnaire): Future[Questionnaire] = {
    val query = questionnaires.filter(_.id === questionnaire.id).update(questionnaire)
    dbConfig().db.run(query).flatMap {
      case 1 => this.read(questionnaire.id)
        .map(_.getOrElse(throw new IllegalStateException("Failed to load questionnaire after updating it")))
      case _ => Future.failed(new IllegalArgumentException(s"Study with id ${questionnaire.id} not found"))
    }
  }

  def delete(id: Long): Future[Int] = {
    dbConfig().db.run(questions.filter(_.questionnaireId === id).result).map(_.size).flatMap {
      case 0 => dbConfig().db.run(questionnaires.filter(_.id === id).delete)
      case _ => Future.failed(new IllegalArgumentException(s"Cannot delete questionnaire $id because it contains questions"))
    }
  }

  def listByStudy(studyId: Long): Future[Seq[Questionnaire]] = {
    dbConfig().db.run(questionnaires.filter(_.studyId === studyId).result)
  }
}
