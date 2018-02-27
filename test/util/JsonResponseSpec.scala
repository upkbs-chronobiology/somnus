package util

import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue

class JsonResponseSpec extends PlaySpec with BeforeAndAfterEach {

  private var response: JsValue = _

  override def beforeEach(): Unit = {
    super.beforeEach()

    response = JsonResponse("foo bar")
  }

  "JsonResponse" should {

    "yield a JSON object" in {
      response.isInstanceOf[JsObject] must equal(true)
    }

    "add a property 'message'" in {
      response("message").as[String] must equal("foo bar")
    }
  }
}
