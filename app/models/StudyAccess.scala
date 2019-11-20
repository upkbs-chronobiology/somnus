package models

import scala.concurrent.Future

import javax.inject.Inject
import javax.inject.Singleton
import models.AccessLevel.AccessLevel
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Writes
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile
import slick.lifted.Tag

case class StudyAccess(userId: Long, studyId: Long, level: AccessLevel)

object StudyAccess {
  val LevelJsonKey = "level"

  implicit val implicitWrites = new Writes[StudyAccess] {
    override def writes(sa: StudyAccess): JsValue = Json.obj(
      "userId" -> sa.userId,
      "studyId" -> sa.studyId,
      "level" -> sa.level.toString
    )
  }

  val tupled = (this.apply _).tupled
}

class StudyAccessTable(tag: Tag) extends Table[StudyAccess](tag, "study_access") {
  def userId = column[Long]("user_id")
  def studyId = column[Long]("study_id")
  def level = column[AccessLevel]("level")

  override def * = (userId, studyId, level) <> (StudyAccess.tupled, StudyAccess.unapply)
}

@Singleton
class StudyAccessRepository @Inject()(dbConfigProvider: DatabaseConfigProvider) {

  private def studyAccesses = TableQuery[StudyAccessTable]

  private def dbConfig = dbConfigProvider.get[JdbcProfile]

  private def find(userId: Long, studyId: Long) =
    studyAccesses.filter(sa => sa.userId === userId && sa.studyId === studyId)

  def listByUser(userId: Long): Future[Seq[StudyAccess]] = {
    dbConfig.db.run(studyAccesses.filter(_.userId === userId).result)
  }

  def listByStudy(studyId: Long): Future[Seq[StudyAccess]] = {
    dbConfig.db.run(studyAccesses.filter(_.studyId === studyId).result)
  }

  def read(userId: Long, studyId: Long): Future[Option[StudyAccess]] = {
    dbConfig.db.run(find(userId, studyId).result.headOption)
  }

  /** If that study-user combination already exists update; add otherwise. */
  def upsert(value: StudyAccess): Future[Int] = {
    dbConfig.db.run(studyAccesses.insertOrUpdate(value))
  }

  def delete(userId: Long, studyId: Long): Future[Int] = {
    dbConfig.db.run(find(userId, studyId).delete)
  }
}
