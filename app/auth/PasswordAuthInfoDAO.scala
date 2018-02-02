package auth

import javax.inject.Inject

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import models.Password
import models.PasswordRepository
import models.UserRepository

class PasswordAuthInfoDAO @Inject()(
  userRepository: UserRepository,
  passwordRepository: PasswordRepository
) extends DelegableAuthInfoDAO[PasswordInfo] {

  override def find(loginInfo: LoginInfo): Future[Option[PasswordInfo]] = {
    this.findPasswordId(loginInfo).flatMap {
      case None => Future.successful(None)
      case Some(pwId) =>
        passwordRepository.get(pwId).map(_.map(
          password => PasswordInfo(password.hasher, password.hash, password.salt)
        ))
    }
  }

  private def findPasswordId(loginInfo: LoginInfo): Future[Option[Long]] = {
    userRepository.retrieve(loginInfo).flatMap {
      case None => Future.failed(new IllegalArgumentException(s"User for loginInfo $loginInfo not found"))
      case Some(user) => Future.successful(user.passwordId)
    }
  }

  override def add(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] = {
    val password = passwordRepository.add(Password(0, authInfo.password, authInfo.salt, authInfo.hasher))
    val updatedUsers = password.flatMap(p => userRepository.updatePassword(loginInfo, Some(p.id)))

    updatedUsers.flatMap {
      case 0 => Future.failed(new IllegalStateException("User for LoginInfo not found"))
      case _ => Future.successful(authInfo)
    }
  }

  override def update(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] = {
    this.findPasswordId(loginInfo).flatMap {
      case None => Future.failed(new IllegalArgumentException(s"Password for loginInfo $loginInfo not found"))
      case Some(pwId) =>
        passwordRepository.update(pwId, Password(0, authInfo.password, authInfo.salt, authInfo.hasher)).flatMap {
          case 0 => Future.failed(new IllegalStateException("User or password not found; update failed"))
          case _ => Future.successful(authInfo)
        }
    }
  }

  override def save(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] = {
    this.find(loginInfo).flatMap {
      case Some(_) => this.update(loginInfo, authInfo)
      case None => this.add(loginInfo, authInfo)
    }
  }

  override def remove(loginInfo: LoginInfo): Future[Unit] = {
    this.findPasswordId(loginInfo).flatMap {
      case None => Future.failed(new IllegalArgumentException(s"Password entry for loginInfo $loginInfo not found"))
      case Some(pwId) =>
        // pw table entry deletion is not sync-critical, therefore not waited upon
        val _ = passwordRepository.delete(pwId)
        userRepository.removePassword(loginInfo)
    }.map(_ => {}) // XXX: Can we avoid this?
  }
}
