package models

import auth.roles.Role
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Injecting
import testutil.FreshDatabase
import testutil.TestUtils

class StudyAccessSpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with FreshDatabase with TestUtils {

  private lazy val repo = inject[StudyAccessRepository]
  private lazy val users = inject[UserRepository]
  private lazy val studies = inject[StudyRepository]

  private lazy val user = doSync(users.create(User(0, "Fooser", None, Some(Role.Researcher.toString))))
  private lazy val study = doSync(studies.create(Study(0, "My study")))

  "StudyAccessRepository" should {
    "create, read, update, delete items" in {
      doSync(repo.upsert(StudyAccess(user.id, study.id, AccessLevel.Write)))

      val first = doSync(repo.read(user.id, study.id))
      first must equal(Some(StudyAccess(user.id, study.id, AccessLevel.Write)))

      doSync(repo.upsert(StudyAccess(user.id, study.id, AccessLevel.Read)))

      val second = doSync(repo.read(user.id, study.id))
      second must equal(Some(StudyAccess(user.id, study.id, AccessLevel.Read)))

      doSync(repo.delete(user.id, study.id))

      doSync(repo.read(user.id, study.id)) must equal(None)
    }
  }
}
