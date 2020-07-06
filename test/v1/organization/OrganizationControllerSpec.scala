package v1.organization

import scala.concurrent.ExecutionContext.Implicits.global

import auth.roles.Role
import models.Organization
import models.OrganizationRepository
import models.UserRepository
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.test.Helpers._
import play.api.test.Injecting
import testutil.Authenticated
import testutil.FreshDatabase
import testutil.TestUtils
import util.Futures.IterableFutureExtensions

class OrganizationControllerSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with Injecting
    with FreshDatabase
    with TestUtils
    with Authenticated
    with BeforeAndAfterEach {

  private lazy val organizationRepo = inject[OrganizationRepository]
  private lazy val userRepo = inject[UserRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()

    // remove organizations from users
    doSync(userRepo.setOrganization(baseUser.id, None))
    doSync(userRepo.setOrganization(researchUser.id, None))
    doSync(userRepo.setOrganization(adminUser.id, None))

    // delete all organizations
    doSync(
      organizationRepo
        .listAll()
        .mapIterableAsync(o => organizationRepo.delete(o.id))
    )
  }

  "OrganizationController" when {
    "not logged in" should {
      "reject requests" in {
        val sampleOrg = organizationForm("Sample Organization")

        status(doRequest(GET, "/v1/organizations")) must equal(UNAUTHORIZED)
        status(doRequest(GET, "/v1/organizations/123")) must equal(UNAUTHORIZED)
        status(doRequest(POST, "/v1/organizations", Some(sampleOrg))) must equal(UNAUTHORIZED)
        status(doRequest(PUT, "/v1/organizations/123", Some(sampleOrg))) must equal(UNAUTHORIZED)
        status(doRequest(DELETE, "/v1/organizations/123")) must equal(UNAUTHORIZED)
      }
    }

    "logged in as base user" should {
      "only list own organization" in {
        val ownOrg = doSync(organizationRepo.create(Organization(0, "Foo Bar")))
        doSync(organizationRepo.create(Organization(0, "Baz")))
        doSync(userRepo.setOrganization(baseUser.id, Some(ownOrg.id)))

        val response = doAuthenticatedRequest(GET, "/v1/organizations")
        status(response) must equal(OK)
        val list = contentAsJson(response).as[JsArray].value.map(_("name").as[String])
        list.length must equal(1)
        list.head must equal("Foo Bar")
      }

      "get own organization" in {
        val ownOrg = doSync(organizationRepo.create(Organization(0, "Foo Bar")))
        doSync(organizationRepo.create(Organization(0, "Baz")))
        doSync(userRepo.setOrganization(baseUser.id, Some(ownOrg.id)))

        val response = doAuthenticatedRequest(GET, s"/v1/organizations/${ownOrg.id}")
        status(response) must equal(OK)
        contentAsJson(response).apply("name").as[String] must equal("Foo Bar")
      }

      "reject getting other organizations" in {
        val ownOrg = doSync(organizationRepo.create(Organization(0, "Foo Bar")))
        val otherOrg = doSync(organizationRepo.create(Organization(0, "Baz")))
        doSync(userRepo.setOrganization(baseUser.id, Some(ownOrg.id)))

        val response = doAuthenticatedRequest(GET, s"/v1/organizations/${otherOrg.id}")
        status(response) must equal(FORBIDDEN)
      }

      "reject modification requests" in {
        val sampleOrg = organizationForm("Sample Organization")

        status(doAuthenticatedRequest(POST, "/v1/organizations", Some(sampleOrg))) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(PUT, "/v1/organizations/123", Some(sampleOrg))) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(DELETE, "/v1/organizations/123")) must equal(FORBIDDEN)
      }
    }

    "logged in as researcher" should {
      implicit val researcher: Role.Value = Role.Researcher

      "only list own organization" in {
        val ownOrg = doSync(organizationRepo.create(Organization(0, "Foo Bar")))
        doSync(organizationRepo.create(Organization(0, "Baz")))
        doSync(userRepo.setOrganization(researchUser.id, Some(ownOrg.id)))

        val response = doAuthenticatedRequest(GET, "/v1/organizations")
        status(response) must equal(OK)
        val list = contentAsJson(response).as[JsArray].value.map(_("name").as[String])
        list.length must equal(1)
        list.head must equal("Foo Bar")
      }

      "get own organization" in {
        val ownOrg = doSync(organizationRepo.create(Organization(0, "Foo Bar")))
        doSync(organizationRepo.create(Organization(0, "Baz")))
        doSync(userRepo.setOrganization(researchUser.id, Some(ownOrg.id)))

        val response = doAuthenticatedRequest(GET, s"/v1/organizations/${ownOrg.id}")
        status(response) must equal(OK)
        contentAsJson(response).apply("name").as[String] must equal("Foo Bar")
      }

      "reject getting other organizations" in {
        val ownOrg = doSync(organizationRepo.create(Organization(0, "Foo Bar")))
        val otherOrg = doSync(organizationRepo.create(Organization(0, "Baz")))
        doSync(userRepo.setOrganization(researchUser.id, Some(ownOrg.id)))

        val response = doAuthenticatedRequest(GET, s"/v1/organizations/${otherOrg.id}")
        status(response) must equal(FORBIDDEN)
      }

      "reject modification requests" in {
        val sampleOrg = organizationForm("Sample Organization")

        status(doAuthenticatedRequest(POST, "/v1/organizations", Some(sampleOrg))) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(PUT, "/v1/organizations/123", Some(sampleOrg))) must equal(FORBIDDEN)
        status(doAuthenticatedRequest(DELETE, "/v1/organizations/123")) must equal(FORBIDDEN)
      }
    }

    "logged in as admin" should {
      implicit val admin: Role.Value = Role.Admin

      "list organizations" in {
        doSync(organizationRepo.create(Organization(0, "Foo Bar")))
        doSync(organizationRepo.create(Organization(0, "Baz")))

        val response = doAuthenticatedRequest(GET, "/v1/organizations")
        status(response) must equal(OK)
        val names = contentAsJson(response).as[JsArray].value.map(_("name").as[String])
        names.length must equal(2)
        names must contain theSameElementsAs Seq("Foo Bar", "Baz")
      }

      "create organizations" in {
        val response = doAuthenticatedRequest(POST, "/v1/organizations", Some(organizationForm("Newbo")))
        status(response) must equal(CREATED)
        val created = contentAsJson(response)
        created("name").as[String] must equal("Newbo")

        doSync(organizationRepo.get(created("id").as[Long])).value.name must equal("Newbo")
      }

      "read organizations" in {
        val id = doSync(organizationRepo.create(Organization(0, "Foo Bar"))).id

        val response = doAuthenticatedRequest(GET, s"/v1/organizations/$id")
        status(response) must equal(OK)
        contentAsJson(response).apply("name").as[String] must equal("Foo Bar")
      }

      "update organizations" in {
        val id = doSync(organizationRepo.create(Organization(0, "Foo Bar"))).id

        val response = doAuthenticatedRequest(PUT, s"/v1/organizations/$id", Some(organizationForm("A New Name™")))
        status(response) must equal(OK)
        val updated = contentAsJson(response)
        updated("name").as[String] must equal("A New Name™")

        doSync(organizationRepo.get(id)).value.name must equal("A New Name™")
      }

      "delete organizations" in {
        val id = doSync(organizationRepo.create(Organization(0, "Foo Bar"))).id

        val response = doAuthenticatedRequest(DELETE, s"/v1/organizations/$id")
        status(response) must equal(OK)

        doSync(organizationRepo.get(id)) must be(None)
      }

      "reject getting nonexistent organization" in {
        status(doAuthenticatedRequest(GET, "/v1/organizations/999")) must equal(NOT_FOUND)
      }

      "reject updating nonexistent organization" in {
        val response = doAuthenticatedRequest(PUT, "/v1/organizations/999", Some(organizationForm("A New Name")))
        status(response) must equal(NOT_FOUND)
      }

      "reject deleting nonexistent organization" in {
        status(doAuthenticatedRequest(DELETE, "/v1/organizations/999")) must equal(NOT_FOUND)
      }
    }
  }

  private def organizationForm(name: String): JsValue = {
    Json.obj("name" -> name)
  }
}
