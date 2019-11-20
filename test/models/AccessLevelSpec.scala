package models

import org.scalatestplus.play.PlaySpec

class AccessLevelSpec extends PlaySpec {

  "AccessLevel" should {
    "correctly order levels" in {
      AccessLevel.Own >= AccessLevel.Own must equal(true)
      AccessLevel.Own >= AccessLevel.Write must equal(true)
      AccessLevel.Own >= AccessLevel.Read must equal(true)

      AccessLevel.Write >= AccessLevel.Own must equal(false)
      AccessLevel.Write >= AccessLevel.Write must equal(true)
      AccessLevel.Write >= AccessLevel.Read must equal(true)

      AccessLevel.Read >= AccessLevel.Own must equal(false)
      AccessLevel.Read >= AccessLevel.Write must equal(false)
      AccessLevel.Read >= AccessLevel.Read must equal(true)
    }
  }
}
