package registration.models

import org.specs2.mutable.Specification
import play.api.libs.json.{JsError, JsSuccess, Json}

class LegacyRegistrationSpec extends Specification {
  "The LegacyTopic" should {
    "parse a standard legacy topic" in {
      LegacyTopic.jf.reads(Json.parse("""{"type": "abc", "name": "def"}""")) shouldEqual JsSuccess(LegacyTopic("abc", "def"))
    }
    "parse a legacy topic with the android workaround" in {
      LegacyTopic.jf.reads(Json.parse("""{"type": "abc", "id": "def"}""")) shouldEqual JsSuccess(LegacyTopic("abc", "def"))
    }
    "fail to parse wrong json" in {
      LegacyTopic.jf.reads(Json.parse("""{"type": "abc", "idf": "def"}""")) should haveClass[JsError]
    }
  }
}
