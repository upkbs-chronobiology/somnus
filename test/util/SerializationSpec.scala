package util

import org.scalatestplus.play.PlaySpec

class SerializationSpec extends PlaySpec {

  "Serialization" should {
    "de-serialize comma-separated lists" in {
      Serialization.parseList("") must equal(Seq(""))
      Serialization.parseList("foo") must equal(Seq("foo"))
      Serialization.parseList("foo,bar,baz") must equal(Seq("foo", "bar", "baz"))
      Serialization.parseList("foo,") must equal(Seq("foo", ""))

      Serialization.parseList("foo,x\ny") must equal(Seq("foo", "x\ny"))
      Serialization.parseList("[foo;bar,]") must equal(Seq("[foo;bar", "]"))
    }

    "serialize lists" in {
      Serialization.serialize(Seq()) must equal(null)
      Serialization.serialize(Seq("")) must equal("")
      Serialization.serialize(Seq("", "foo")) must equal(",foo")
      Serialization.serialize(Seq("foo", "bar", "baz")) must equal("foo,bar,baz")
      Serialization.serialize(Seq("foo", "", "baz")) must equal("foo,,baz")

      Serialization.serialize(Seq("foo_bar", "baz")) must equal("foo_bar,baz")
      Serialization.serialize(Seq("foo_", "_")) must equal("foo_,_")
      Serialization.serialize(Seq("foo_,", "_,")) must equal("foo_\\,,_\\,")
      Serialization.serialize(Seq("___")) must equal("___")
      Serialization.serialize(Seq("_\\__")) must equal("_\\\\__")

      Serialization.serialize(Seq("foo", "x\ny")) must equal("foo,x\ny")
      Serialization.serialize(Seq("[foo;bar", "]")) must equal("[foo;bar,]")
    }

    "de-serialize int ranges" in {
      Serialization.parseIntRange("0,1") must equal(InclusiveRange(0, 1))
      Serialization.parseIntRange("-1,3") must equal(InclusiveRange(-1, 3))
      Serialization.parseIntRange("99,99") must equal(InclusiveRange(99, 99))

      an[IllegalArgumentException] must be thrownBy {
        Serialization.parseIntRange("")
      }
      an[IllegalArgumentException] must be thrownBy {
        Serialization.parseIntRange("1,2,3")
      }
      an[IllegalArgumentException] must be thrownBy {
        Serialization.parseIntRange(",2")
      }
      an[IllegalArgumentException] must be thrownBy {
        Serialization.parseIntRange("2.5,4")
      }
      an[IllegalArgumentException] must be thrownBy {
        Serialization.parseIntRange("test")
      }
      an[IllegalArgumentException] must be thrownBy {
        Serialization.parseIntRange("1,foo")
      }
    }

    "de-serialize float ranges" in {
      Serialization.parseFloatRange("0,1") must equal(InclusiveRange(0, 1))
      Serialization.parseFloatRange("0.1,1.2") must equal(InclusiveRange(0.1f, 1.2f))
      Serialization.parseFloatRange("-5.,3") must equal(InclusiveRange(-5, 3))
      Serialization.parseFloatRange("99.99,99.99") must equal(InclusiveRange(99.99f, 99.99f))

      an[IllegalArgumentException] must be thrownBy {
        Serialization.parseFloatRange("")
      }
      an[IllegalArgumentException] must be thrownBy {
        Serialization.parseFloatRange("1.5,2,3")
      }
      an[IllegalArgumentException] must be thrownBy {
        Serialization.parseFloatRange(",2")
      }
      an[IllegalArgumentException] must be thrownBy {
        Serialization.parseFloatRange("test")
      }
      an[IllegalArgumentException] must be thrownBy {
        Serialization.parseFloatRange("1,foo.7")
      }
    }

    "serialize ranges" in {
      Serialization.serialize(InclusiveRange(0, 1)) must equal("0,1")
      Serialization.serialize(InclusiveRange(-2, 5)) must equal("-2,5")
      Serialization.serialize(InclusiveRange(1.1f, 3)) must equal("1.1,3.0")
      Serialization.serialize(InclusiveRange(0.000f, 0.000)) must equal("0.0,0.0")
    }
  }
}
