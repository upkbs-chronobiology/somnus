package modules

import scala.concurrent.ExecutionContext.Implicits.global

import auth.DefaultEnv
import auth.PasswordAuthInfoDAO
import auth.TokenRepository
import auth.UserGenerator
import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.api.EventBus
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.SilhouetteProvider
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.impl.util.SecureRandomIDGenerator
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository
import models.UserRepository
import models.UserService
import net.codingwell.scalaguice.ScalaModule
import play.api.Configuration

class SilhouetteModule extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    bind[Silhouette[DefaultEnv]].to[SilhouetteProvider[DefaultEnv]]
    bind[UserService].to[UserRepository]
    bind[DelegableAuthInfoDAO[PasswordInfo]].to[PasswordAuthInfoDAO]

    bind[IDGenerator].toInstance(new SecureRandomIDGenerator)
    bind[Clock].toInstance(Clock())
    bind[PasswordHasher].toInstance(new BCryptPasswordHasher)

    bind[UserGenerator].asEagerSingleton()
  }

  @Provides
  def providePasswordHasherRegistry(
    passwordHasher: PasswordHasher
  ): PasswordHasherRegistry = {
    PasswordHasherRegistry(passwordHasher)
  }

  @Provides
  def provideEnvironment(
    userService: UserService,
    authenticatorService: AuthenticatorService[BearerTokenAuthenticator],
    eventBus: EventBus): Environment[DefaultEnv] = {

    Environment[DefaultEnv](
      userService,
      authenticatorService,
      Seq(),
      eventBus
    )
  }

  @Provides
  def provideAuthenticatorService(
    idGenerator: IDGenerator,
    clock: Clock): AuthenticatorService[BearerTokenAuthenticator] = {

    // no custom config at the moment - otherwise, read from configuration here
    val config = BearerTokenAuthenticatorSettings()

    new BearerTokenAuthenticatorService(config, TokenRepository, idGenerator, clock)
  }

  @Provides
  def provideCredentialsProvider(
    authInfoRepository: AuthInfoRepository,
    passwordHasherRegistry: PasswordHasherRegistry): CredentialsProvider = {

    new CredentialsProvider(authInfoRepository, passwordHasherRegistry)
  }

  @Provides
  def provideAuthInfoRepository(passwordInfoDAO: DelegableAuthInfoDAO[PasswordInfo]): AuthInfoRepository = {
    new DelegableAuthInfoRepository(passwordInfoDAO)
  }
}
