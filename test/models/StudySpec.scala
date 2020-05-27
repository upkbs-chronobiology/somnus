package models

import auth.AuthService
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Injecting
import testutil.TestUtils

class StudySpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with TestUtils {

  val authService = inject[AuthService]
  val studies = inject[StudyRepository]

  val jeff: User = doSync(authService.register("Jeff Tuttle", Some("12345678")))
  val rebecca: User = doSync(authService.register("Rebecca Sinclair", Some("87654321")))
  val maria: User = doSync(authService.register("Maria Theresa Short", Some("00001111")))
  val study: Study = doSync(studies.create(Study(0, "My Study")))

  "Study" should {
    "add, remove and list study participants" in {
      doSync(studies.addParticipant(study.id, jeff.id)) must equal(1)

      val studyUsers = doSync(studies.listParticipants(study.id))
      studyUsers.length must equal(1)
      studyUsers.head.name must equal("Jeff Tuttle")

      doSync(studies.addParticipant(study.id, rebecca.id)) must equal(1)

      val studyUsersLater = doSync(studies.listParticipants(study.id))
      studyUsersLater.length must equal(2)
      studyUsersLater.map(_.name) must contain("Rebecca Sinclair")

      val userStudies = doSync(studies.listForParticipant(jeff.id))
      userStudies.length must equal(1)
      userStudies.head.name must equal("My Study")

      doSync(studies.removeParticipant(study.id, jeff.id)) must equal(1)
      doSync(studies.listParticipants(study.id)).length must equal(1)
      doSync(studies.removeParticipant(study.id, rebecca.id)) must equal(1)
      doSync(studies.listParticipants(study.id)).length must equal(0)
    }

    "not remove non-participant" in {
      doSync(studies.removeParticipant(study.id, maria.id)) must equal(0)
    }
  }
}
