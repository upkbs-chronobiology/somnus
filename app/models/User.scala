package models

import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import auth.roles.Role.Role
import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.api.Identity
import com.mohiva.play.silhouette.api.LoginInfo
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Writes
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile
import slick.lifted.Tag

case class User(id: Long, name: String, passwordId: Option[Long], role: Option[String] = None) extends Identity

object User {
  implicit val implicitWrites = new Writes[User] {
    def writes(user: User): JsValue = {
      Json.obj(
        "name" -> user.name,
        "role" -> user.role,
        "id" -> user.id
      )
    }
  }

  val tupled = (this.apply _).tupled
}

class UserTable(tag: Tag) extends Table[User](tag, "user") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.Unique)
  def passwordId = column[Long]("password_id", O.Unique)
  def password = foreignKey("password", passwordId.?, TableQuery[PasswordTable])(_.id)
  def role = column[String]("role")

  override def * = (id, name, passwordId.?, role.?) <> (User.tupled, User.unapply)
}

trait UserService extends IdentityService[User]

// TODO: Create initial user (e.g. admin) here?
@Singleton
class UserRepository @Inject()(dbConfigProvider: DatabaseConfigProvider) extends UserService {

  def users = TableQuery[UserTable]

  def dbConfig = dbConfigProvider.get[JdbcProfile]

  def create(user: User): Future[User] = {
    dbConfig.db.run((users returning users.map(_.id)) += user)
      .flatMap(this.get(_).flatMap {
        case None => Future.failed(new IllegalStateException("User could not be loaded after creation"))
        case Some(u) => Future.successful(u)
      })
  }

  def get(id: Long): Future[Option[User]] = {
    dbConfig.db.run(users.filter(_.id === id).result.headOption)
  }

  def get(name: String): Future[Option[User]] = {
    dbConfig.db.run(users.filter(_.name === name).result.headOption)
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

  def delete(name: String): Future[Int] = {
    dbConfig.db.run(users.filter(_.name === name).delete)
  }

  def setRole(id: Long, role: Role): Future[Int] = {
    val query = users.filter(_.id === id).map(_.role.?).update(Option(role).map(_.toString))
    dbConfig.db.run(query)
  }

  def setRole(name: String, role: Role): Future[Int] = {
    val query = users.filter(_.name === name).map(_.role.?).update(Option(role).map(_.toString))
    dbConfig.db.run(query)
  }
}
