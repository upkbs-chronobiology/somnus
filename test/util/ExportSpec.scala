package util

import java.sql.Timestamp
import java.time.OffsetDateTime

import models.Answer
import org.scalatestplus.play.PlaySpec

class ExportSpec extends PlaySpec {

  "Export" should {
    "serialize answers to CSV" in {
      val csv = Export.asCsv(Seq("id", "questionId", "content", "userId", "timestamp", "timestampLocal"), Seq(
        Answer(0, 1, "Foo bar", 2, new Timestamp(0), OffsetDateTime.parse("2018-05-16T14:08:43+02:00")),
        Answer(3, 4, "Foo, bar; and baz.", 5, new Timestamp(0), OffsetDateTime.parse("2099-01-01T01:01:55-11:30")),
        Answer(6, 7, "What are those \"quotes\"?", 8, new Timestamp(0), OffsetDateTime.parse("2000-01-01T00:00:00+00:00"))
      ))
      csv must equal(
        """"id","questionId","content","userId","timestamp","timestampLocal"
          |"0","1","Foo bar","2","1970-01-01T00:00:00Z","2018-05-16T14:08:43+02:00"
          |"3","4","Foo, bar; and baz.","5","1970-01-01T00:00:00Z","2099-01-01T01:01:55-11:30"
          |"6","7","What are those ""quotes""?","8","1970-01-01T00:00:00Z","2000-01-01T00:00Z"
          |""".stripMargin)
    }

    "properly serialize Options to CSV" in {
      val csv = Export.asCsv(Seq("Foo", "Bar", "Baz Booze"), Seq(
        (Some("A"), Some(99), None)
      ))
      csv must equal(
        """"Foo","Bar","Baz Booze"
          |"A","99",
          |""".stripMargin)
    }

    "properly serialize nulls to CSV" in {
      val csv = Export.asCsv(Seq(null, null, null, null), Seq(
        (null, "test", null, "jest")
      ))
      csv must equal(
        """,,,
          |,"test",,"jest"
          |""".stripMargin)
    }

    "throw in case of wrong headers shape" in {
      an[IllegalArgumentException] shouldBe thrownBy {
        Export.asCsv(Seq("a", "b"), Seq(
          (1, 2, 3)
        ))
      }
      an[IllegalArgumentException] shouldBe thrownBy {
        Export.asCsv(Seq("a", "b", "c"), Seq(
          (1, 2)
        ))
      }
      an[IllegalArgumentException] shouldBe thrownBy {
        Export.asCsv(Seq(), Seq(
          (1, 2, 3)
        ))
      }
    }
  }
}
