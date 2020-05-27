package util

import play.api.libs.json.Json
import play.api.libs.json.JsValue

object JsonResponse {
  def apply(message: String): JsValue = {
    Json.obj("message" -> message)
  }
}

object JsonSuccess {
  def apply(message: String): JsValue = {
    JsonResponse(message)
  }
}

object JsonError {
  def apply(message: String): JsValue = {
    JsonResponse(message)
  }
}
