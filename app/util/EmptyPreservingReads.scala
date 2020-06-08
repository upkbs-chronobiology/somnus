package util

import scala.collection.Seq
import scala.language.postfixOps

import play.api.libs.json.JsArray
import play.api.libs.json.JsError
import play.api.libs.json.JsNull
import play.api.libs.json.JsResult
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Reads

object EmptyPreservingReads {

  private def parseArrayStrings(seq: Seq[JsValue]): JsResult[Seq[String]] = {
    try {
      JsSuccess(
        seq.map(
          item =>
            item.validate[String] match {
              case s: JsSuccess[String] => s.get
              case e: JsError =>
                throw new IllegalArgumentException(s"Expected string, got: $item\nErrors: ${e.errors.mkString(", ")}")
            }
        )
      )
    } catch {
      case e: IllegalArgumentException =>
        JsError(e.getMessage)
    }
  }

  implicit val readsStringSeq = new Reads[Option[Seq[String]]] {
    override def reads(json: JsValue): JsResult[Option[Seq[String]]] = json match {
      case JsNull => JsSuccess(None)
      case JsArray(a) => parseArrayStrings(a).map(Some(_))
      case _ => JsError("Failed to parse array")
    }
  }
}
