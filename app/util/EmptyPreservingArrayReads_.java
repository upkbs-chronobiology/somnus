package util;

import models.Question;
import play.api.libs.json.JsValue;
import play.api.libs.json.Reads;
import scala.collection.Seq;

public class EmptyPreservingArrayReads_ {

//    val optionalDoubleReads = new Reads[Option[Double]] {
//        def reads (json:JsValue) =json match {
//        case JsNumber(n) =>JsSuccess(Some(n.toDouble))
//        case JsString(n) =>JsSuccess(Some(n.toDouble))
//        case JsNull =>JsSuccess(None)             // The important one
//        case _ =>JsError("error.expected.jsnumber")
//    }
//    }
}
