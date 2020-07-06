package models

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Injecting
import testutil.Authenticated
import testutil.FreshDatabase
import testutil.TestUtils

class OrganizationSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with Injecting
    with FreshDatabase
    with TestUtils
    with Authenticated {

  val organizationRepo = inject[OrganizationRepository]

  "Organization" should {
    "create, read, update, delete organizations" in {
      val created = doSync(organizationRepo.create(Organization(0, "Test Org")))
      created.id must be >= 1L
      created.name must equal("Test Org")

      val listed = doSync(organizationRepo.listAll())
      listed.length must equal(1)
      listed.head.id must equal(created.id)
      listed.head.name must equal("Test Org")

      val updated = doSync(organizationRepo.update(Organization(created.id, "New Name")))
      updated.value.name must equal("New Name")

      val got = doSync(organizationRepo.get(created.id))
      got.value.name must equal("New Name")

      val deleted = doSync(organizationRepo.delete(created.id))
      deleted must equal(1)

      doSync(organizationRepo.listAll()).length must equal(0)
    }
  }
}
