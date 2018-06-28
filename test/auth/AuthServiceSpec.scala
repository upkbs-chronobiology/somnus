package auth

import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Injecting
import testutil.TestUtils

class AuthServiceSpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with TestUtils {

  private val authService = inject[AuthService]
  private val credentialsProvider = inject[CredentialsProvider]

  "AuthService" should {
    "register users" in {
      val user = doSync(authService.register("User A", Some("12345678")))

      user.name must equal("User A")

      val loginInfo = doSync(credentialsProvider.authenticate(Credentials("User A", "12345678")))
      loginInfo.providerKey must equal("User A")
    }

    "unregister users" in {
      val user = doSync(authService.register("User B", Some("12345678")))

      user.name must equal("User B")

      doSync(authService.unregister("User B"))

      val authFuture = credentialsProvider.authenticate(Credentials("User B", "12345678"))
      ScalaFutures.whenReady(authFuture.failed) { e =>
        e mustBe an[IllegalArgumentException]
      }
    }

    "refuse to register with existing names, case-insensitively" in {
      val user = doSync(authService.register("Johnny B. Goode", Some("12345678")))

      user.name must equal("Johnny B. Goode")

      Seq("Johnny B. Goode", "johnny b. goode", "jOhNNy b. GOoDe") foreach { username =>
        val eventualUser = authService.register(username, Some("12345678"))
        ScalaFutures.whenReady(eventualUser.failed) { e =>
          e mustBe an[IllegalArgumentException]
          e.getMessage must include("exists")
        }
      }
    }
  }
}
