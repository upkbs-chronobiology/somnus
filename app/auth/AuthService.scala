package auth

import javax.inject.Inject

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.PasswordHasher
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import models.{User, UserRepository, UserService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthService @Inject()(
  userService: UserService,
  userRepository: UserRepository,
  authInfoRepository: AuthInfoRepository,
  credentialsProvider: CredentialsProvider,
  passwordHasher: PasswordHasher
) {

  def register(username: String, password: String): Future[User] = {
    val loginInfo = LoginInfo(credentialsProvider.id, username)
    userService.retrieve(loginInfo).flatMap {
      case Some(_) => throw new IllegalArgumentException("user already exists")
      case None =>
        for {
          user <- userRepository.create(User(0, loginInfo.providerKey, None))
          _ <- authInfoRepository.add(loginInfo, passwordHasher.hash(password))
        } yield
          // TODO: Log in?
          user
    } recover {
      // TODO: Proper logging in case of errors
      case e: Exception => throw new IllegalStateException("Failed to register user", e)
    }
  }

  def unregister(username: String): Future[_] = {
    (for {
      // FIXME: removing password entry doesn't work - throws weird exception
      // _ <- authInfoRepository.remove(LoginInfo(credentialsProvider.id, username))
      _ <- userRepository.delete(username)
    } yield {}) recover {
      case e: Exception => throw new IllegalStateException("Failed to unregister user", e)
    }
  }
}
