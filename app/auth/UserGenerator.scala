package auth

import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

import auth.roles.Role
import models.UserRepository

/** Generator for default users, like the initial admin.
  */
@Singleton
class UserGenerator @Inject()(userRepository: UserRepository, authService: AuthService) {

  private val AdminName = "somnus"
  private val PasswordLength = 10

  userRepository.get(AdminName).map {
    case None => createAdmin()
  }

  private def createAdmin() = {
    val password = Random.alphanumeric.take(PasswordLength).mkString
    for {
      _ <- authService.register(AdminName, password)
      _ <- userRepository.setRole(AdminName, Role.Admin)
    } yield println(s"Created admin user '$AdminName' with password: $password\nChange it ASAP!")
  }
}
