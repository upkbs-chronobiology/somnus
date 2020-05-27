package models

import java.sql.Timestamp
import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.libs.json.Writes
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile
import slick.lifted.Tag

case class PwReset(id: Long, token: String, expiry: Timestamp, userId: Long)

object PwReset {
  implicit val implicitWrites = new Writes[PwReset] {
    def writes(pwReset: PwReset): JsValue = {
      Json.obj("token" -> pwReset.token, "expiry" -> pwReset.expiry, "userId" -> pwReset.userId)
    }
  }

  val tupled = (this.apply _).tupled
}

class PwResetTable(tag: Tag) extends Table[PwReset](tag, "pw_reset") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def token = column[String]("token", O.Unique)
  def expiry = column[Timestamp]("expiry")
  def userId = column[Long]("user_id")

  def user = foreignKey("user", userId, TableQuery[UserTable])(_.id)

  override def * = (id, token, expiry, userId) <> (PwReset.tupled, PwReset.unapply)
}

@Singleton
class PwResetsRepository @Inject() (dbConfigProvider: DatabaseConfigProvider) {

  private def pwResets = TableQuery[PwResetTable]

  private def dbConfig = dbConfigProvider.get[JdbcProfile]

  def getByToken(token: String): Future[Option[PwReset]] = {
    dbConfig.db.run(pwResets.filter(_.token === token).result.headOption)
  }

  def create(pwReset: PwReset): Future[PwReset] = {
    dbConfig.db
      .run((pwResets returning pwResets.map(_.id)) += pwReset)
      .flatMap(newId => dbConfig.db.run(pwResets.filter(_.id === newId).result.head))
  }

  def delete(id: Long): Future[Int] = {
    dbConfig.db.run(pwResets.filter(_.id === id).delete)
  }
}
