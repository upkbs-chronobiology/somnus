package models

import play.api.Play
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile
import slick.lifted.Tag

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Password(id: Long, hash: String, salt: Option[String], hasher: String)

class PasswordTable(tag: Tag) extends Table[Password](tag, "password") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def hash = column[String]("hash")
  def salt = column[Option[String]]("salt")
  def hasher = column[String]("hasher")

  override def * = (id, hash, salt, hasher) <> (Password.tupled, Password.unapply)
}

object Passwords {
  def passwords = TableQuery[PasswordTable]

  // XXX: hacky (see other examples)
  def dbConfig() = DatabaseConfigProvider.get[JdbcProfile](Play.current)

  def add(password: Password): Future[Password] = {
    dbConfig().db.run((passwords returning passwords.map(_.id)) += password)
      .flatMap(this.get(_).map(_.get))
  }

  def get(id: Long): Future[Option[Password]] = {
    dbConfig().db.run(passwords.filter(_.id === id).result.headOption)
  }

  def update(id: Long, password: Password): Future[Int] = {
    dbConfig().db.run(passwords.filter(_.id === id).update(password))
  }

  def delete(id: Long): Future[Int] = {
    dbConfig().db.run(passwords.filter(_.id === id).delete)
  }
}
