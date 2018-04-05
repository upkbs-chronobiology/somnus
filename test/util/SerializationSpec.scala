package util

import org.scalatestplus.play.PlaySpec

class SerializationSpec extends PlaySpec {

  "Serialization" should {
    "de-serialize comma-separated strings" in {
      Serialization.parseList("") must equal(Seq(""))
      Serialization.parseList("foo") must equal(Seq("foo"))
      Serialization.parseList("foo,bar,baz") must equal(Seq("foo", "bar", "baz"))
      Serialization.parseList("foo,") must equal(Seq("foo", ""))

      Serialization.parseList("foo,x\ny") must equal(Seq("foo", "x\ny"))
      Serialization.parseList("[foo;bar,]") must equal(Seq("[foo;bar", "]"))
    }

    "serialize" in {
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
  }
}
