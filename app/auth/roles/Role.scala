package auth.roles

object Role extends Enumeration {

  type Role = Value

  /** Master of disaster. Allowed to do everything™. */
  val Admin = Value("admin")

  /** Role allowing a user to edit questions and see (anyone's) answers. */
  val Researcher = Value("researcher")
}
