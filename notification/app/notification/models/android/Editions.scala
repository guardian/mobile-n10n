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

    def fromTopic(t: Topic): Option[Edition] = condOpt(t) {
      case Topic(TopicTypes.Breaking, "uk") => UK
      case Topic(TopicTypes.Breaking, "us") => US
      case Topic(TopicTypes.Breaking, "au") => AU
      case Topic(TopicTypes.Breaking, "international") => International
    }

    implicit val jf = new Format[Edition] {
      override def reads(json: JsValue): JsResult[Edition] = json.validate[String] flatMap {
        case "uk" => JsSuccess(UK)
        case "us" => JsSuccess(US)
        case "au" => JsSuccess(AU)
        case "international" => JsSuccess(International)
        case unknown => JsError(s"Unkown region [$unknown]")
      }
      override def writes(region: Edition): JsValue = JsString(region.toString)
    }
  }
}