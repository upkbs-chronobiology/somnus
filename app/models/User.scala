package models

import com.mohiva.play.silhouette.api.{Identity, LoginInfo}
import com.mohiva.play.silhouette.api.services.IdentityService
import play.api.Play
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile
import slick.lifted.Tag

import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

// TODO: How/where to include LoginInfo?
case class User(id: Long, name: String, pwHash: String) extends Identity

class UserTable(tag: Tag) extends Table[User](tag, "user") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.Unique)
  def pwHash = column[String]("pw_hash")

  override def * = (id, name, pwHash) <> (User.tupled, User.unapply)
}

trait UserService extends IdentityService[User]

object Users extends UserService {
  def users = TableQuery[UserTable]

  // XXX: hacky - 1. deprecated functions, 2. function instead of val
  // Maybe create global service for dbConfig only updating if play app changed?
  def dbConfig() = DatabaseConfigProvider.get[JdbcProfile](Play.current)

  def create(user: User): Future[User] = {
    dbConfig().db.run((users returning users.map(_.id)) += user)
      .flatMap(this.get(_).map(_.get))
  }

  def get(id: Long): Future[Option[User]] = {
    dbConfig().db.run(users.filter(_.id === id).result.headOption)
  }

  override def retrieve(loginInfo: LoginInfo): Future[Option[User]] = {
    // assume we only have simple login (by user name)
    val name = loginInfo.providerKey
    dbConfig().db.run(users.filter(_.name === name).result.headOption)
  }
}
