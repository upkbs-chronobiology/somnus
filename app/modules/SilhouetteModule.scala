package modules

import auth.{DefaultEnv, PasswordAuthInfoDAO, TokenRepository, UserGenerator}
import com.google.inject.{AbstractModule, Provides}
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.api.{Environment, EventBus, Silhouette, SilhouetteProvider}
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.impl.util.SecureRandomIDGenerator
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository
import models.{UserRepository, UserService}
import net.codingwell.scalaguice.ScalaModule
import play.api.Configuration

import scala.concurrent.ExecutionContext.Implicits.global

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
    configuration: Configuration,
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
