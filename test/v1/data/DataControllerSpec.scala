package v1.data

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

import scala.collection.mutable.ListBuffer

import auth.roles.Role
import models.Study
import models.StudyRepository
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers._
import play.api.test.Injecting
import testutil.Authenticated
import testutil.FreshDatabase
import testutil.TestUtils

class DataControllerSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with Injecting
    with FreshDatabase
    with TestUtils
    with Authenticated {

  val study = doSync(inject[StudyRepository].create(Study(0, "Foo Bar Study")))

  "DataController" when {
    "not logged in" should {
      "reject exports" in {
        status(doRequest(GET, s"/v1/data/studies/${study.id}/csv/zip")) must equal(UNAUTHORIZED)
      }
    }

    "logged in as base user" should {
      "reject exports" in {
        status(doAuthenticatedRequest(GET, s"/v1/data/studies/${study.id}/csv/zip")) must equal(FORBIDDEN)
      }
    }

    "logged in as researcher" should {
      implicit val _ = Role.Researcher

      "404 on inexistent studies" in {
        status(doAuthenticatedRequest(GET, "/v1/data/studies/999/csv/zip")) must equal(NOT_FOUND)
      }

      "serve per-study zipped csv exports" in {
        val response = doAuthenticatedRequest(GET, s"/v1/data/studies/${study.id}/csv/zip")
        status(response) must equal(OK)

        val contentStream = new ByteArrayInputStream(contentAsBytes(response).toArray)
        val zipStream = new ZipInputStream(contentStream, StandardCharsets.UTF_8)

        val entries = ListBuffer[ZipEntry]()
        var zipEntry = zipStream.getNextEntry
        while (zipEntry != null) {
          entries += zipEntry
          zipEntry = zipStream.getNextEntry
        }
        zipStream.close()

        entries.length must equal(6)
        entries.map(_.getName) must contain allOf (
          "studies.csv",
          "questionnaires.csv",
          "questions.csv",
          "answers.csv",
          "users.csv",
          "schedules.csv"
        )
        // TODO: Verify CSV format of files?
      }
    }
  }
}
