package util

import java.sql.Timestamp

import models.Answer
import org.scalatestplus.play.PlaySpec

class ExportSpec extends PlaySpec {

  "Export" should {
    "serialize answers to CSV" in {
      val csv = Export.asCsv(Seq("id", "questionId", "content", "userId", "timestamp"), Seq(
        Answer(0, 1, "Foo bar", 2, new Timestamp(0)),
        Answer(3, 4, "Foo, bar; and baz.", 5, new Timestamp(0)),
        Answer(6, 7, "What are those \"quotes\"?", 8, new Timestamp(0))
      ))
      csv must equal(
        """"id","questionId","content","userId","timestamp"
          |"0","1","Foo bar","2","1970-01-01T00:00:00Z"
          |"3","4","Foo, bar; and baz.","5","1970-01-01T00:00:00Z"
          |"6","7","What are those ""quotes""?","8","1970-01-01T00:00:00Z"
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
