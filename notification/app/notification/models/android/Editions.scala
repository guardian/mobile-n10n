package notification.models.android

import models.{Topic, TopicTypes}
import play.api.data.validation.ValidationError
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
    implicit val jf = new Format[Edition] {
      override def reads(json: JsValue): JsResult[Edition] = json.validate[String].collect(ValidationError(s"Unkown region")) {
        case "uk" => UK
        case "us" => US
        case "au" => AU
        case "international" => International
      }

      override def writes(region: Edition): JsValue = JsString(region.toString)
    }
  }
}