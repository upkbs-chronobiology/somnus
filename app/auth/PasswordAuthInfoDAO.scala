package auth

import javax.inject.Inject

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import models.{Password, Passwords, UserRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PasswordAuthInfoDAO @Inject()(userRepository: UserRepository) extends DelegableAuthInfoDAO[PasswordInfo] {

  override def find(loginInfo: LoginInfo): Future[Option[PasswordInfo]] = {
    userRepository.retrieve(loginInfo).flatMap {
      case Some(user) if user.passwordId.isDefined =>
        Passwords.get(user.passwordId.get).map(_.map(
          p => PasswordInfo(p.hasher, p.hash, p.salt)
        ))
    }
  }

  override def add(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] = {
    val password = Passwords.add(Password(0, authInfo.password, authInfo.salt, authInfo.hasher))
    val updatedUsers = password.flatMap(password => userRepository.updatePassword(loginInfo, Some(password.id)))

    updatedUsers.map {
      case 0 => throw new IllegalStateException("User for LoginInfo not found")
      case 1 => authInfo
    }
  }

  override def update(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] = {
    userRepository.retrieve(loginInfo).flatMap {
      case Some(user) if user.passwordId.isDefined =>
        Passwords.update(
          user.passwordId.get,
          Password(0, authInfo.password, authInfo.salt, authInfo.hasher)
        ).map {
          case 0 => throw new IllegalStateException("User or password not found; update failed")
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
    userRepository.retrieve(loginInfo).map {
      case Some(user) if user.passwordId.isDefined =>
        // pw table entry deletion is not sync-critical, therefore not waited upon
        Passwords.delete(user.passwordId.get)
        userRepository.removePassword(loginInfo)
    }
  }
}
