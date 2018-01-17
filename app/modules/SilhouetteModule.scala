package modules

import auth.{DefaultEnv, TokenRepository}
import com.google.inject.{AbstractModule, Provides}
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.api.util.{Clock, IDGenerator}
import com.mohiva.play.silhouette.api.{Environment, EventBus, Silhouette, SilhouetteProvider}
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.util.SecureRandomIDGenerator
import models.{UserService, Users}
import net.codingwell.scalaguice.ScalaModule
import play.api.Configuration

import scala.concurrent.ExecutionContext.Implicits.global

class SilhouetteModule extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    bind[Silhouette[DefaultEnv]].to[SilhouetteProvider[DefaultEnv]]
    bind[UserService].toInstance(Users)

    bind[IDGenerator].toInstance(new SecureRandomIDGenerator())
    bind[Clock].toInstance(Clock())
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
}
