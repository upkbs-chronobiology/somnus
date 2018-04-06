package util

import play.api.libs.json.JsNumber
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Writes

// XXX: Cannot use upper bound for T because there's no global number supertype in Scala
case class InclusiveRange[T](min: T, max: T)

object InclusiveRange {

  private def buildJson(min: BigDecimal, max: BigDecimal) = {
    Json.obj(
      "min" -> JsNumber(min),
      "max" -> JsNumber(max)
    )
  }

  implicit val implicitWrites = new Writes[InclusiveRange[BigDecimal]] {
    def writes(range: InclusiveRange[BigDecimal]): JsValue = {
      buildJson(range.min, range.max)
    }
  }

  implicit val implicitWritesInt = new Writes[InclusiveRange[Int]] {
    def writes(range: InclusiveRange[Int]): JsValue = {
      buildJson(range.min, range.max)
    }
  }

  implicit val implicitWritesFloat = new Writes[InclusiveRange[Float]] {
    def writes(range: InclusiveRange[Float]): JsValue = {
      buildJson(BigDecimal(range.min), BigDecimal(range.max))
    }
  }
}
