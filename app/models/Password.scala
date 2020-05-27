package models

import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile
import slick.lifted.Tag

case class Password(id: Long, hash: String, salt: Option[String], hasher: String)

class PasswordTable(tag: Tag) extends Table[Password](tag, "password") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def hash = column[String]("hash")
  def salt = column[Option[String]]("salt")
  def hasher = column[String]("hasher")

  override def * = (id, hash, salt, hasher) <> (Password.tupled, Password.unapply)
}

@Singleton
class PasswordRepository @Inject()(dbConfigProvider: DatabaseConfigProvider) {
  def passwords = TableQuery[PasswordTable]

  def dbConfig = dbConfigProvider.get[JdbcProfile]

  def add(password: Password): Future[Password] = {
    dbConfig.db.run((passwords returning passwords.map(_.id)) += password)
      .flatMap(this.get(_).flatMap {
        case None => Future.failed(new IllegalStateException("Failed to load password after creation"))
        case Some(pw) => Future.successful(pw)
      })
  }

  def get(id: Long): Future[Option[Password]] = {
    dbConfig.db.run(passwords.filter(_.id === id).result.headOption)
  }

  def update(password: Password): Future[Int] = {
    dbConfig.db.run(passwords.filter(_.id === password.id).update(password))
  }

  def delete(id: Long): Future[Int] = {
    dbConfig.db.run(passwords.filter(_.id === id).delete)
  }
}
