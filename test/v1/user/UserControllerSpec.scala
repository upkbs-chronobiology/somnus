package v1.user

import auth.AuthService
import auth.roles.Role
import models.AccessLevel
import models.Organization
import models.OrganizationRepository
import models.Study
import models.StudyAccess
import models.StudyAccessRepository
import models.StudyRepository
import models.User
import models.UserRepository
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.JsArray
import play.api.libs.json.JsNull
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.JsString
import play.api.test.Helpers._
import play.api.test.Injecting
import testutil.Authenticated

class UserControllerSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with Injecting
    with BeforeAndAfterAll
    with Authenticated {

  private lazy val organizationRepo = inject[OrganizationRepository]
  private lazy val userRepo = inject[UserRepository]

  var donald = doSync(inject[AuthService].register("Donald Duck", Some("00112233")))

  override def beforeAll(): Unit = {
    super.beforeAll()

    val studyRepo = inject[StudyRepository]
    val study = doSync(studyRepo.create(Study(0, "Test Study")))

    doSync(inject[StudyAccessRepository].upsert(StudyAccess(researchUser.id, study.id, AccessLevel.Read)))

    doSync(studyRepo.addParticipant(study.id, donald.id))
    doSync(studyRepo.addParticipant(study.id, baseUser.id))

    val secretStudy = doSync(studyRepo.create(Study(0, "Noyb Study")))

    doSync(studyRepo.addParticipant(secretStudy.id, donald.id))
    doSync(studyRepo.addParticipant(secretStudy.id, baseUser.id))

    val organization = doSync(organizationRepo.create(Organization(0, "Sample Labs")))
    doSync(userRepo.setOrganization(researchUser.id, Some(organization.id)))
    researchUser = researchUser.copy(organizationId = Some(organization.id))
    doSync(userRepo.setOrganization(donald.id, Some(organization.id)))
    donald = donald.copy(organizationId = Some(organization.id))
  }

  "UserController" when {
    "not logged in" should {
      "reject listing users" in {
        status(doRequest(GET, "/v1/users")) must equal(401)
      }

      "reject creating users" in {
        status(doRequest(POST, "/v1/users", Some(userCreationJson("Dagobert Duck")))) must equal(UNAUTHORIZED)
      }
    }

    "logged in as basic user" should {
      "reject listing users" in {
        status(doAuthenticatedRequest(GET, "/v1/users")) must equal(403)
      }

      "reject listing studies of other users" in {
        status(doAuthenticatedRequest(GET, s"/v1/users/${donald.id}/studies")) must equal(403)
      }

      "list studies of current user" in {
        val response = doAuthenticatedRequest(GET, s"/v1/users/${baseUser.id}/studies")

        status(response) must equal(200)

        val list = contentAsJson(response).as[JsArray].value
        list.length must equal(2)
        list.head.apply("name").as[String] must equal("Test Study")
        list(1).apply("name").as[String] must equal("Noyb Study")
      }

      "reject creating users" in {
        status(doAuthenticatedRequest(POST, "/v1/users", Some(userCreationJson("Dagobert Duck")))) must equal(FORBIDDEN)
      }
    }

    "logged in as editor" should {
      implicit val r = Role.Researcher

      "list users" in {
        val response = doAuthenticatedRequest(GET, "/v1/users")

        status(response) must equal(200)

        val list = contentAsJson(response).as[JsArray].value
        list.length must be >= 0
        list.map(_("name").as[String]) must contain(donald.name)
      }

      "list studies of other users based on ACLs" in {
        val response = doAuthenticatedRequest(GET, s"/v1/users/${donald.id}/studies")

        status(response) must equal(200)

        val list = contentAsJson(response).as[JsArray].value
        list.length must equal(1)
        list.head.apply("name").as[String] must equal("Test Study")
      }

      "reject updating users in different organization" in {
        val org = doSync(organizationRepo.create(Organization(0, "Other Org")))
        val user = doSync(userRepo.create(User(0, "Other-org Guy", None, None, Some(org.id))))

        val response =
          doAuthenticatedRequest(PUT, s"/v1/users/${user.id}", Some(userUpdateJson(Role.Researcher.toString)))

        status(response) must equal(FORBIDDEN)
      }

      "update role of user in same organization" in {
        val user = doSync(userRepo.create(User(0, "Same-org Guy", None, None, researchUser.organizationId)))

        val response =
          doAuthenticatedRequest(PUT, s"/v1/users/${user.id}", Some(userUpdateJson(Role.Researcher.toString)))

        status(response) must equal(OK)
        doSync(userRepo.get(user.id)).value.role.value must equal(Role.Researcher.toString)
      }

      "not change user organizations" in {
        val user = doSync(userRepo.create(User(0, "Same-org Guy 2", None, None, researchUser.organizationId)))

        val response =
          doAuthenticatedRequest(PUT, s"/v1/users/${user.id}", Some(userUpdateJson(null, Some(123))))

        status(response) must equal(OK)
        doSync(userRepo.get(user.id)).value.organizationId must equal(researchUser.organizationId)
      }

      "create users under same organization" in {
        val response = doAuthenticatedRequest(POST, "/v1/users", Some(userCreationJson("My Participant")))
        status(response) must equal(CREATED)
        contentAsJson(response).apply("organizationId").as[Long] must equal(researchUser.organizationId.get)
      }
    }

    "logged in as admin" should {
      implicit val r = Role.Admin

      "reject invalid roles" in {
        val response = doAuthenticatedRequest(
          PUT,
          s"/v1/users/${donald.id}",
          Some(userUpdateJson("fooRole")),
          role = Some(Role.Admin)
        )

        status(response) must equal(400)
      }

      "allow updating users" in {
        val response = doAuthenticatedRequest(
          PUT,
          s"/v1/users/${donald.id}",
          Some(userUpdateJson(Role.Researcher.toString)),
          role = Some(Role.Admin)
        )

        status(response) must equal(200)
        doSync(inject[UserRepository].get(donald.id)).get.role must equal(Some(Role.Researcher.toString))
      }

      "accept user updates with null-role" in {
        val response =
          doAuthenticatedRequest(PUT, s"/v1/users/${donald.id}", Some(userUpdateJson(null)), role = Some(Role.Admin))

        status(response) must equal(200)
        doSync(inject[UserRepository].get(donald.id)).get.role must equal(None)
      }

      "reject removing own admin rights" in {
        val response =
          doAuthenticatedRequest(PUT, s"/v1/users/${adminUser.id}", Some(userUpdateJson(null)), role = Some(Role.Admin))

        status(response) must equal(400)
        doSync(inject[UserRepository].get(adminUser.id)).get.role must equal(Some(Role.Admin.toString))
      }

      "create user" in {
        val response = doAuthenticatedRequest(POST, "/v1/users", Some(userCreationJson("Dagobert Duck")))
        status(response) must equal(CREATED)

        val createdUser = contentAsJson(response)
        createdUser("name").as[String] must equal("Dagobert Duck")
        createdUser("id").as[Long] must be >= 0L
      }

      "refuse to create user with already existing name" in {
        val response = doAuthenticatedRequest(POST, "/v1/users", Some(userCreationJson("Daisy Duck")))
        status(response) must equal(CREATED)

        val secondResponse = doAuthenticatedRequest(POST, "/v1/users", Some(userCreationJson("Daisy Duck")))
        status(secondResponse) must equal(BAD_REQUEST)
      }

      "list all studies of other users" in {
        val response = doAuthenticatedRequest(GET, s"/v1/users/${donald.id}/studies")

        status(response) must equal(200)

        val list = contentAsJson(response).as[JsArray].value
        list.length must equal(2)
        list(0).apply("name").as[String] must equal("Test Study")
        list(1).apply("name").as[String] must equal("Noyb Study")
      }

      "not change user organizations" in {
        val org = doSync(organizationRepo.create(Organization(0, "Example Org")))

        val response =
          doAuthenticatedRequest(PUT, s"/v1/users/${donald.id}", Some(userUpdateJson(null, Some(org.id))))

        status(response) must equal(OK)
        doSync(userRepo.get(donald.id)).value.organizationId.value must equal(org.id)
      }
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Product", "org.wartremover.warts.Serializable"))
  def userUpdateJson(role: String, organizationId: Option[Long] = None): JsObject = {
    Json.obj("role" -> role, "organizationId" -> organizationId.map(_.toString).map(JsString).orElse(Some(JsNull)))
  }

  def userCreationJson(name: String): JsObject = Json.obj("name" -> name)
}
