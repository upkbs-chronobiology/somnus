package models

import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.data.Form
import play.api.data.Forms._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.libs.json.Writes
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._
import slick.lifted.Tag

case class Study(id: Long, name: String)

object Study {
  implicit val implicitWrites = new Writes[Study] {
    def writes(study: Study): JsValue = {
      Json.obj("id" -> study.id, "name" -> study.name)
    }
  }

  val tupled = (this.apply _).tupled
}

case class StudyFormData(name: String)

object StudyForm {
  val form = Form(mapping("name" -> nonEmptyText)(StudyFormData.apply)(StudyFormData.unapply))
}

class StudyTable(tag: Tag) extends Table[Study](tag, "study") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.Unique)

  override def * = (id, name) <> (Study.tupled, Study.unapply)
}

@Singleton
class StudyRepository @Inject() (
  dbConfigProvider: DatabaseConfigProvider,
  questionnaires: QuestionnairesRepository,
  studyAccessRepo: StudyAccessRepository
) {

  def studies = TableQuery[StudyTable]
  def studyParticipants = TableQuery[StudyParticipantsTable]
  def users = TableQuery[UserTable]

  def dbConfig = dbConfigProvider.get[MySQLProfile]

  def listAll(): Future[Seq[Study]] = dbConfig.db.run(studies.result)

  def create(study: Study): Future[Study] = {
    // This existence check is not thread safe, but that's acceptable because the same constraint is
    // implemented on db level. This is just for more human-friendly feedback in case of collisions.
    // XXX: Does this constraint even make sense, especially given limited visibility through ACLs?
    dbConfig.db.run(studies.filter(_.name === study.name).result.headOption) flatMap {
      case Some(_) => throw new IllegalArgumentException("A study with the same name already exists")
      case None =>
        val query = (studies returning studies.map(_.id)) += study
        dbConfig.db
          .run(query)
          .flatMap(
            id =>
              this
                .read(id)
                .map(_.getOrElse(throw new IllegalStateException("Failed to load study after creation")))
          )
    }
  }

  def read(id: Long): Future[Option[Study]] = {
    dbConfig.db.run(studies.filter(_.id === id).result.headOption)
  }

  def update(study: Study): Future[Study] = {
    val query = studies.filter(_.id === study.id).update(study)
    dbConfig.db.run(query).flatMap {
      case 1 =>
        this
          .read(study.id)
          .map(_.getOrElse(throw new IllegalStateException("Failed to load study after updating it")))
      case _ => throw new IllegalArgumentException(s"Study with id ${study.id} not found")
    }
  }

  def delete(id: Long): Future[Int] = {
    for {
      participants <- listParticipants(id)
      questionnaires <- questionnaires.listByStudy(id)
      result <- if (questionnaires.nonEmpty)
        Future.failed(
          new IllegalArgumentException(s"Cannot delete study with id $id because it contains questionnaires")
        )
      else if (participants.nonEmpty)
        Future.failed(new IllegalArgumentException(s"Cannot delete study with id $id because it contains participants"))
      else {
        for {
          sas <- studyAccessRepo.listByStudy(id)
          _ <- Future.sequence(sas.map(sa => studyAccessRepo.delete(sa)))
          deleted <- dbConfig.db.run(studies.filter(_.id === id).delete)
        } yield deleted
      }
    } yield result
  }

  def listParticipants(studyId: Long): Future[Seq[User]] = {
    // XXX: Check if study actually exists? Currently, an empty seq will be returned.
    val study = studies.filter(_.id === studyId)
    val studiesAndUsers = (study join studyParticipants on (_.id === _.studyId))
      .join(users) on (_._2.userId === _.id)
    val query = for {
      ((_, _), participants) <- studiesAndUsers
    } yield participants
    dbConfig.db.run(query.result)
  }

  def addParticipant(studyId: Long, userId: Long): Future[Int] = {
    dbConfig.db.run(studyParticipants += StudyParticipant(userId, studyId))
  }

  def removeParticipant(studyId: Long, userId: Long): Future[Int] = {
    val query = studyParticipants.filter(_.studyId === studyId).filter(_.userId === userId).delete
    dbConfig.db.run(query)
  }

  def listForParticipant(userId: Long): Future[Seq[Study]] = {
    val user = users.filter(_.id === userId)
    val usersAndStudies = (user join studyParticipants on (_.id === _.userId))
      .join(studies) on (_._2.studyId === _.id)
    val query = for {
      ((_, _), studies) <- usersAndStudies
    } yield studies
    dbConfig.db.run(query.result)
  }
}
