package v1.acl

import scala.concurrent.Future

import auth.roles.Role
import models.AccessLevel
import models.AccessLevel.AccessLevel
import models.Study
import models.StudyRepository
import models.User
import models.UserRepository
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.Injecting
import testutil.Authenticated
import testutil.FreshDatabase
import testutil.TestUtils

class AclControllerSpec extends PlaySpec
  with GuiceOneAppPerSuite with FreshDatabase with Injecting with TestUtils with Authenticated {

  val studyRepo = inject[StudyRepository]
  val userRepo = inject[UserRepository]

  val study = doSync(studyRepo.create(Study(0, "Sample Study")))
  val user = doSync(userRepo.create(User(0, "Sample User", None)))

  "AclController" when {
    "not logged in" should {
      "reject listing study acls by study" in {
        val response = doRequest(GET, s"/v1/studies/${study.id}/acls")
        status(response) must equal(UNAUTHORIZED)
      }

      "reject listing study acls by user" in {
        val response = doRequest(GET, s"/v1/users/${user.id}/acls")
        status(response) must equal(UNAUTHORIZED)
      }

      "reject setting acls (studies path)" in {
        val response = doRequest(PUT, s"/v1/studies/${study.id}/acls/${user.id}", Some(levelJson(AccessLevel.Own)))
        status(response) must equal(UNAUTHORIZED)
      }

      "reject setting acls (users path)" in {
        val response = doRequest(PUT, s"/v1/users/${user.id}/acls/${study.id}", Some(levelJson(AccessLevel.Own)))
        status(response) must equal(UNAUTHORIZED)
      }

      "reject deleting acls (users path)" in {
        val response = doRequest(DELETE, s"/v1/users/${user.id}/acls/${study.id}")
        status(response) must equal(UNAUTHORIZED)
      }

      "reject deleting acls (studies path)" in {
        val response = doRequest(DELETE, s"/v1/studies/${study.id}/acls/${user.id}")
        status(response) must equal(UNAUTHORIZED)
      }
    }

    // TODO: Test with non-admin authorized users

    "logged in as admin" should {
      implicit val _ = Role.Admin

      "list, set, delete acls (through studies)" in {
        val initialReadResponse = doAuthenticatedRequest(GET, s"/v1/studies/${study.id}/acls")
        status(initialReadResponse) must equal(OK)
        contentAsJson(initialReadResponse).as[JsArray].value.size must equal(0)

        val setResponse1 = doAuthenticatedRequest(PUT, s"/v1/studies/${study.id}/acls/${user.id}", Some(levelJson(AccessLevel.Write)))
        status(setResponse1) must equal(OK)

        assertSingleAcl(doAuthenticatedRequest(GET, s"/v1/studies/${study.id}/acls"), "write")

        val setResponse2 = doAuthenticatedRequest(PUT, s"/v1/studies/${study.id}/acls/${user.id}", Some(levelJson(AccessLevel.Read)))
        //        val setResponse2 = doAuthenticatedRequest(PUT, s"/v1/users/${user.id}/acls/${study.id}", Some(levelJson(AccessLevel.Read)))
        status(setResponse2) must equal(OK)

        assertSingleAcl(doAuthenticatedRequest(GET, s"/v1/studies/${study.id}/acls"), "read")

        val deleteResponse = doAuthenticatedRequest(DELETE, s"/v1/studies/${study.id}/acls/${user.id}")
        status(deleteResponse) must equal(OK)

        val finalReadResponse = doAuthenticatedRequest(GET, s"/v1/studies/${study.id}/acls")
        status(finalReadResponse) must equal(OK)
        contentAsJson(finalReadResponse).as[JsArray].value.size must equal(0)
      }

      "list, set, delete acls (through users)" in {
        val initialReadResponse = doAuthenticatedRequest(GET, s"/v1/users/${user.id}/acls")
        status(initialReadResponse) must equal(OK)
        contentAsJson(initialReadResponse).as[JsArray].value.size must equal(0)

        val setResponse1 = doAuthenticatedRequest(PUT, s"/v1/users/${user.id}/acls/${study.id}", Some(levelJson(AccessLevel.Write)))
        status(setResponse1) must equal(OK)

        assertSingleAcl(doAuthenticatedRequest(GET, s"/v1/users/${user.id}/acls"), "write")

        val setResponse2 = doAuthenticatedRequest(PUT, s"/v1/users/${user.id}/acls/${study.id}", Some(levelJson(AccessLevel.Read)))
        status(setResponse2) must equal(OK)

        assertSingleAcl(doAuthenticatedRequest(GET, s"/v1/users/${user.id}/acls"), "read")

        val deleteResponse = doAuthenticatedRequest(DELETE, s"/v1/users/${user.id}/acls/${study.id}")
        status(deleteResponse) must equal(OK)

        val finalReadResponse = doAuthenticatedRequest(GET, s"/v1/users/${user.id}/acls")
        status(finalReadResponse) must equal(OK)
        contentAsJson(finalReadResponse).as[JsArray].value.size must equal(0)
      }

      "behave equally through studies and users path" in {
        contentAsJson(doAuthenticatedRequest(GET, s"/v1/studies/${study.id}/acls")).as[JsArray].value.size must equal(0)

        doSync(doAuthenticatedRequest(PUT, s"/v1/users/${user.id}/acls/${study.id}", Some(levelJson(AccessLevel.Write))))

        assertSingleAcl(doAuthenticatedRequest(GET, s"/v1/studies/${study.id}/acls"), "write")
        assertSingleAcl(doAuthenticatedRequest(GET, s"/v1/users/${user.id}/acls"), "write")

        doSync(doAuthenticatedRequest(DELETE, s"/v1/users/${user.id}/acls/${study.id}"))

        contentAsJson(doAuthenticatedRequest(GET, s"/v1/studies/${study.id}/acls")).as[JsArray].value.size must equal(0)
        contentAsJson(doAuthenticatedRequest(GET, s"/v1/users/${user.id}/acls")).as[JsArray].value.size must equal(0)
      }

      "return status 400 for invalid object" in {
        val badJson = Json.obj("level" -> "this-is-wrong")
        val response = doAuthenticatedRequest(PUT, s"/v1/users/${user.id}/acls/${study.id}", Some(badJson))
        status(response) must equal(BAD_REQUEST)
      }
    }
  }

  private def assertSingleAcl(response: Future[Result], level: String) = {
    status(response) must equal(OK)

    val acls3 = contentAsJson(response).as[JsArray].value
    acls3.size must equal(1)
    acls3(0) must equal(Json.obj(
      "userId" -> user.id,
      "studyId" -> study.id,
      "level" -> level
    ))
  }

  private def levelJson(level: AccessLevel): JsValue = Json.obj("level" -> level.toString)
}
