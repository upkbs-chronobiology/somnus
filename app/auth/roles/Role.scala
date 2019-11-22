package auth.roles

import models.User
import util.Logging

object Role extends Enumeration with Logging {

  private val BaseLevel = 0

  type Role = Value

  /** Master of disaster. Allowed to do everything™. */
  val Admin = Value("admin")

  /** Role allowing a user to edit questions and see (anyone's) answers. */
  val Researcher = Value("researcher")

  def level(role: Role): Int = {
    role match {
      case Admin => 2
      case Researcher => 1
      case _ => BaseLevel
    }
  }

  def level(role: Option[Role]): Int = {
    role.map(level).getOrElse(BaseLevel)
  }

  def level(user: User): Int = {
    try {
      user.role.map(r => Role.level(Role.withName(r))).getOrElse(BaseLevel)
    } catch {
      case e: NoSuchElementException =>
        logger.error("Unknown String value for enum Role", e)
        BaseLevel
    }
  }
}
