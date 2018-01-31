package auth

import javax.inject.{Inject, Singleton}

import auth.roles.Role
import models.UserRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

/** Generator for default users, like the initial admin.
  */
@Singleton
class UserGenerator @Inject()(userRepository: UserRepository, authService: AuthService) {

  private val AdminName = "somnus"

  userRepository.get(AdminName).map {
    case None => createAdmin()
  }

  private def createAdmin() = {
    val password = Random.alphanumeric.take(10).mkString
    for {
      _ <- authService.register(AdminName, password)
      _ <- userRepository.setRole(AdminName, Role.Admin)
    } yield println(s"Created admin user '$AdminName' with password: $password\nChange it ASAP!")
  }
}
