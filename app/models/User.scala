package models

import javax.inject.{Inject, Singleton}

import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.api.{Identity, LoginInfo}
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.{JsValue, Json, Writes}
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile
import slick.lifted.Tag

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// TODO: How/where to include LoginInfo?
case class User(id: Long, name: String, passwordId: Option[Long]) extends Identity

object User {
  implicit val implicitWrites = new Writes[User] {
    def writes(user: User): JsValue = {
      Json.obj(
        "name" -> user.name
      )
    }
  }

  val tupled = (this.apply _).tupled
}

class UserTable(tag: Tag) extends Table[User](tag, "user") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.Unique)
  def passwordId = column[Long]("password_id", O.Unique)
  def password = foreignKey("password", passwordId.?, Passwords.passwords)(_.id)

  override def * = (id, name, passwordId.?) <> (User.tupled, User.unapply)
}

trait UserService extends IdentityService[User]

// TODO: Create initial user (e.g. admin) here?
@Singleton
class UserRepository @Inject() (dbConfigProvider: DatabaseConfigProvider) extends UserService {

  def users = TableQuery[UserTable]

  def dbConfig = dbConfigProvider.get[JdbcProfile]

  def create(user: User): Future[User] = {
    dbConfig.db.run((users returning users.map(_.id)) += user)
      .flatMap(this.get(_).map(_.get))
  }

  def get(id: Long): Future[Option[User]] = {
    dbConfig.db.run(users.filter(_.id === id).result.headOption)
  }

  override def retrieve(loginInfo: LoginInfo): Future[Option[User]] = {
    // assume we only have simple login (by user name)
    val name = loginInfo.providerKey
    dbConfig.db.run(users.filter(_.name === name).result.headOption)
  }

  def updatePassword(loginInfo: LoginInfo, passwordId: Option[Long]): Future[Int] = {
    val query = users.filter(_.name === loginInfo.providerKey)
      .map(_.passwordId.?).update(passwordId)
    dbConfig.db.run(query)
  }

  def removePassword(loginInfo: LoginInfo): Future[Int] = {
    updatePassword(loginInfo, None)
  }

  def listAll(): Future[Seq[User]] = {
    dbConfig.db.run(users.result)
  }
}
