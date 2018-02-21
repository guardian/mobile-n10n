package notification.models.android

import models.{Topic, TopicTypes}
import play.api.libs.json._

import scala.PartialFunction.condOpt

object Editions {

  sealed trait Edition

  case object UK extends Edition {
    override val toString = "uk"
  }

  case object US extends Edition {
    override val toString = "us"
  }

  case object AU extends Edition {
    override val toString = "au"
  }

  case object International extends Edition {
    override val toString = "international"
  }

  object Edition {

    val fromString: PartialFunction[String, Edition] = {
      case "uk" => UK
      case "us" => US
      case "au" => AU
      case "international" => International
    }

    implicit val jf = new Format[Edition] {
      override def reads(json: JsValue): JsResult[Edition] = json.validate[String].collect(JsonValidationError(s"Unknown region"))(fromString)

      override def writes(region: Edition): JsValue = JsString(region.toString)
    }
  }
}