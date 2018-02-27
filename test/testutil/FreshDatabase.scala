package testutil

import scala.util.Random

import org.scalatest.TestSuite
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

trait FreshDatabase extends GuiceOneAppPerSuite {
  this: TestSuite =>

  val BaseName = "somnus-test"
  val UrlConfigKey = "slick.dbs.default.db.url"

  // XXX: Can we somehow use parent application (config) and just "extend" here?
  override def fakeApplication(): Application = {
    val defaultUrl = super.fakeApplication().configuration.get[String](UrlConfigKey)

    val random = Random.nextInt(Integer.MAX_VALUE)
    val freshUrl = defaultUrl.replace(BaseName, s"$BaseName-$random")

    GuiceApplicationBuilder().configure(UrlConfigKey -> freshUrl).build()
  }
}
