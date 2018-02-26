package util

import play.api.libs.json.JsValue
import play.api.libs.json.Json

object JsonResponse {
  def apply(message: String): JsValue = {
    Json.toJson({
      "message" -> message
    })
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
