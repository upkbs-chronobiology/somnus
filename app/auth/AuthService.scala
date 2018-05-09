package auth

import java.sql.Timestamp
import java.time.Instant

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.PasswordHasher
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import exceptions.ItemNotFoundException
import javax.inject.Inject
import models.PwReset
import models.PwResetsRepository
import models.User
import models.UserRepository
import models.UserService

class AuthService @Inject()(
  userService: UserService,
  userRepository: UserRepository,
  authInfoRepository: AuthInfoRepository,
  pwResetsRepo: PwResetsRepository,
  credentialsProvider: CredentialsProvider,
  passwordHasher: PasswordHasher
) {

  private val TokenLength = 12

  def register(username: String, password: String): Future[User] = {
    val loginInfo = LoginInfo(credentialsProvider.id, username)
    userService.retrieve(loginInfo).flatMap {
      case Some(_) => Future.failed(new IllegalArgumentException("user already exists"))
      case None =>
        for {
          user <- userRepository.create(User(0, loginInfo.providerKey, None))
          _ <- authInfoRepository.add(loginInfo, passwordHasher.hash(password))
        } yield
          // TODO: Log in?
          user
    } recoverWith {
      // TODO: Proper logging in case of errors
      case e: Exception => Future.failed(new IllegalStateException("Failed to register user", e))
    }
  }

  def unregister(username: String): Future[Unit] = {
    (for {
      _ <- authInfoRepository.remove[PasswordInfo](LoginInfo(credentialsProvider.id, username))
      _ <- userRepository.delete(username)
    } yield {}) recoverWith {
      case e: Exception => Future.failed(new IllegalStateException("Failed to unregister user", e))
    }
  }

  // TODO: Regularly purge old pwReset db entries somehow, somewhere

  def generateResetToken(userId: Long, expiry: Timestamp): Future[PwReset] = {
    userRepository.get(userId) flatMap {
      case None => throw new ItemNotFoundException(s"User with id $userId not found")
      case Some(_) =>
        val token = Random.alphanumeric.take(TokenLength).mkString
        pwResetsRepo.create(PwReset(0, token, expiry, userId))
    }
  }

  def resetPassword(token: String, newPassword: String): Future[PasswordInfo] = {
    pwResetsRepo.getByToken(token) flatMap {
      case None => throw new ItemNotFoundException("Password reset token invalid")
      case Some(pwReset) if pwReset.expiry.before(Timestamp.from(Instant.now())) =>
        pwResetsRepo.delete(pwReset.id)
        throw new IllegalArgumentException("Password reset token has expired")
      case Some(pwReset) =>
        userRepository.get(pwReset.userId) flatMap {
          case None => throw new ItemNotFoundException("User account for this reset token does not exist (anymore)")
          case Some(user) =>
            pwResetsRepo.delete(pwReset.id)

            val loginInfo = LoginInfo(credentialsProvider.id, user.name)
            authInfoRepository.update(loginInfo, passwordHasher.hash(newPassword))
        }
    }
  }
}
