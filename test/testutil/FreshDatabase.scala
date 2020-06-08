package testutil

import org.scalatest.BeforeAndAfterAll
import org.scalatest.TestSuite
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.db.Database
import play.api.db.DBApi
import play.api.db.evolutions.Evolutions
import play.api.test.Injecting

trait FreshDatabase extends GuiceOneAppPerSuite with Injecting with BeforeAndAfterAll {
  this: TestSuite =>

  private lazy val db = inject[DBApi]

  private val defaultDatabase: Database = db.database("default")

  override protected def beforeAll(): Unit = {
    cleanupEvolutions(defaultDatabase)
    initializeEvolutions(defaultDatabase)
  }

  private def initializeEvolutions(database: Database): Unit = {
    Evolutions.applyEvolutions(database)
  }

  private def cleanupEvolutions(database: Database): Unit = {
    // TODO: Find a way to force this, even if Evolutions thinks the db is in an inconsistent state,
    //  perhaps by marking it as resolved programmatically.
    Evolutions.cleanupEvolutions(database)
  }
}
