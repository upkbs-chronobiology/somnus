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
    // if below throws, you might need to add a VM parameter to your run configuration (check README)
    if (!defaultUrl.contains(BaseName))
      throw new IllegalStateException("Missing db base name to replace in URL - are you really using test config?")

    val random = Random.nextInt(Integer.MAX_VALUE)
    val freshUrl = defaultUrl.replace(BaseName, s"$BaseName-$random")

    GuiceApplicationBuilder().configure(UrlConfigKey -> freshUrl).build()
  }
}
