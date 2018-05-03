package util

import java.sql.Timestamp

import models.Answer
import org.scalatestplus.play.PlaySpec

class ExportSpec extends PlaySpec {

  "Export" should {
    "serialize answers to CSV" in {
      val csv = Export.asCsv(Seq(
        Answer(0, 1, "Foo bar", 2, new Timestamp(0)),
        Answer(3, 4, "Foo, bar; and baz.", 5, new Timestamp(0)),
        Answer(6, 7, "What are those \"quotes\"?", 8, new Timestamp(0))
      ))
      csv must equal(
        """"0","1","Foo bar","2","1970-01-01 01:00:00.0"
          |"3","4","Foo, bar; and baz.","5","1970-01-01 01:00:00.0"
          |"6","7","What are those ""quotes""?","8","1970-01-01 01:00:00.0"
          |""".stripMargin)
    }
  }
}
