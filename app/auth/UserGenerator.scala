package auth

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

import auth.roles.Role
import javax.inject.Inject
import javax.inject.Singleton
import models.UserRepository
import util.Logging

/** Generator for default users, like the initial admin.
  */
@Singleton
class UserGenerator @Inject()(userRepository: UserRepository, authService: AuthService) extends Logging {

  private val AdminName = "somnus"
  private val PasswordLength = 10

  userRepository.get(AdminName).foreach {
    case None => createAdmin()
    // XXX: This is ridiculous; just here to avoid compiler/linter warnings
    case Some(_) => Future.unit
  }

  private def createAdmin() = {
    val password = Random.alphanumeric.take(PasswordLength).mkString
    for {
      _ <- authService.register(AdminName, password)
      _ <- userRepository.setRole(AdminName, Some(Role.Admin))
    } yield logger.warn(s"Created admin user '$AdminName' with password: $password\nChange it ASAP!")
  }
}
