package models


import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.data.Form
import play.api.data.Forms._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Writes
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile
import slick.lifted.Tag

case class Study(id: Long, name: String)

object Study {
  implicit val implicitWrites = new Writes[Study] {
    def writes(study: Study): JsValue = {
      Json.obj(
        "id" -> study.id,
        "name" -> study.name
      )
    }
  }

  val tupled = (this.apply _).tupled
}

case class StudyFormData(name: String)

object StudyForm {
  val form = Form(
    mapping(
      "name" -> nonEmptyText
    )(StudyFormData.apply)(StudyFormData.unapply)
  )
}

class StudyTable(tag: Tag) extends Table[Study](tag, "study") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.Unique)

  override def * = (id, name) <> (Study.tupled, Study.unapply)
}

@Singleton
class StudyRepository @Inject()(dbConfigProvider: DatabaseConfigProvider) {

  def studies = TableQuery[StudyTable]

  def dbConfig = dbConfigProvider.get[JdbcProfile]

  def listAll(): Future[Seq[Study]] = dbConfig.db.run(studies.result)

  def create(study: Study): Future[Study] = {
    val query = (studies returning studies.map(_.id)) += study
    dbConfig.db.run(query).flatMap(id => this.read(id)
      .map(_.getOrElse(throw new IllegalStateException("Failed to load study after creation"))))
  }

  def read(id: Long): Future[Option[Study]] = {
    dbConfig.db.run(studies.filter(_.id === id).result.headOption)
  }

  def update(study: Study): Future[Study] = {
    val query = studies.filter(_.id === study.id).update(study)
    dbConfig.db.run(query).flatMap {
      case 1 => this.read(study.id)
        .map(_.getOrElse(throw new IllegalStateException("Failed to load study after updating it")))
      case _ => throw new IllegalArgumentException(s"Study with id ${study.id} not found")
    }
  }

  def delete(id: Long): Future[Int] = {
    dbConfig.db.run(studies.filter(_.id === id).delete)
  }
}
