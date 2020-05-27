package util

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json

class EmptyPreservingReadsSpec extends PlaySpec {

  "EmptyPreservingArrayReads" should {
    implicit val _ = EmptyPreservingReads.readsStringSeq

    "correctly parse standard string arrays" in {
      val json = Json.parse("""["a", "bb", "ccc"]""")

      val seq = json.validate[Option[Seq[String]]].get.get

      seq.size must equal(3)
      seq must contain allOf ("a", "bb", "ccc")
    }

    "preserve empty strings" in {
      val json = Json.parse("""["", ""]""")

      val seq = json.validate[Option[Seq[String]]].get.get

      seq.size must equal(2)
      seq must contain("")
    }

    "correctly parse empty arrays" in {
      val json = Json.parse("""[]""")

      val seq = json.validate[Option[Seq[String]]].get.get

      seq.size must equal(0)
    }

    "map null to None" in {
      val json = Json.parse("""null""")

      json.validate[Option[Seq[String]]].get must equal(None)
    }

    "reject non-string arrays" in {
      val json = Json.parse("""["a", 33]""")

      val result = json.validate[Option[Seq[String]]]

      result.isSuccess must equal(false)
      result.isError must equal(true)
    }
  }
}
