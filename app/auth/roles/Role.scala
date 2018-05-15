package auth.roles

object Role extends Enumeration {

  type Role = Value

  /** Master of disaster. Allowed to do everything™. */
  val Admin = Value("admin")

  /** Role allowing a user to edit questions and see (anyone's) answers. */
  val Researcher = Value("researcher")

  def level(role: Role): Int = {
    role match {
      case Admin => 2
      case Researcher => 1
      case _ => 0
    }
  }

  def level(role: Option[Role]): Int = {
    role.map(level).getOrElse(0)
  }
}
