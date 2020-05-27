package auth

import java.time.Instant
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.authenticators.BearerTokenAuthenticator
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import org.joda.time.DateTime
import org.joda.time.Duration
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Injecting
import testutil.FreshDatabase
import testutil.TestUtils

class TokenRepositorySpec extends PlaySpec with GuiceOneAppPerSuite with FreshDatabase with Injecting with TestUtils {

  val repo = inject[TokenRepository]
  val credentialsProvider = inject[CredentialsProvider]

  "UserSession" should {
    "convert to authenticator correctly" in {
      val session = UserSession(
        "abc",
        "Ronnie O'Sullivan",
        Instant.now(),
        Instant.now().plus(java.time.Duration.ofDays(5)),
        Some(123456)
      )
      val authenticator = UserSession.toBearerTokenAuthenticator(session, credentialsProvider)

      session.id mustEqual authenticator.id
      session.username mustEqual authenticator.loginInfo.providerKey
      session.lastUsed.toEpochMilli mustEqual authenticator.lastUsedDateTime.getMillis
      session.expiry.toEpochMilli mustEqual authenticator.expirationDateTime.getMillis
      session.idleTimeout mustEqual authenticator.idleTimeout.map(_.toSeconds)
    }

    "convert from authenticator correctly" in {
      val authenticator = BearerTokenAuthenticator(
        "xyz",
        new LoginInfo("my-provider", "Siya Kolisi"),
        DateTime.now().minus(Duration.standardHours(4)),
        DateTime.now().plus(Duration.standardDays(3)),
        Some(FiniteDuration(2, TimeUnit.DAYS))
      )

      val session = UserSession.fromBearerTokenAuthenticator(authenticator)

      session.id mustEqual authenticator.id
      session.username mustEqual authenticator.loginInfo.providerKey
      session.lastUsed.toEpochMilli mustEqual authenticator.lastUsedDateTime.getMillis
      session.expiry.toEpochMilli mustEqual authenticator.expirationDateTime.getMillis
      session.idleTimeout mustEqual authenticator.idleTimeout.map(_.toSeconds)
    }
  }

  "TokenRepository" should {
    "add, find, update, and remove authenticator" in {
      val authenticator = BearerTokenAuthenticator(
        "xyz",
        new LoginInfo(credentialsProvider.id, "Siya Kolisi"),
        DateTime.now().minus(Duration.standardHours(4)),
        DateTime.now().plus(Duration.standardDays(3)),
        Some(FiniteDuration(654321, TimeUnit.SECONDS))
      )

      doSync(repo.add(authenticator)) mustEqual authenticator
      doSync(repo.find("xyz")) mustEqual Some(authenticator)

      val updatedAuthenticator = authenticator.copy(lastUsedDateTime = DateTime.now(), idleTimeout = None)
      doSync(repo.update(updatedAuthenticator)) mustEqual updatedAuthenticator
      doSync(repo.find("xyz")) mustEqual Some(updatedAuthenticator)

      doSync(repo.remove("xyz"))
      doSync(repo.find("xyz")) mustEqual None
    }

    "throw if adding the same twice" in {
      val authenticator = BearerTokenAuthenticator(
        "foo-id",
        new LoginInfo("my-provider", "Siya Kolisi"),
        DateTime.now().minus(Duration.standardHours(4)),
        DateTime.now().plus(Duration.standardDays(3)),
        Some(FiniteDuration(654321, TimeUnit.SECONDS))
      )

      doSync(repo.add(authenticator)) mustEqual authenticator
      intercept[IllegalArgumentException] {
        doSync(repo.add(authenticator))
      }.getMessage must include("already exists")
    }
  }
}
