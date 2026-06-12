package models

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class Registration(
  deviceToken: DeviceToken,
  platform: Platform,
  topics: Set[Topic],
  buildTier: Option[String],
  appVersion: Option[String]
)

object Registration {
  private val validTopicsReads: Reads[Set[Topic]] = Reads { json =>
    json.validate[JsArray].map { arr =>
      arr.value.flatMap(_.validate[Topic].asOpt).toSet
    }
  }

  private val validTopicsFormat: Format[Set[Topic]] = Format(
    validTopicsReads,
    Writes[Set[Topic]](topics => Json.toJson(topics.toList))
  )

  implicit val registrationJF: Format[Registration] = (
    (__ \ "deviceToken").format[DeviceToken] and
    (__ \ "platform").format[Platform] and
    (__ \ "topics").format[Set[Topic]](validTopicsFormat) and
    (__ \ "buildTier").formatNullable[String] and
    (__ \ "appVersion").formatNullable[String]
  )(Registration.apply, r => (r.deviceToken, r.platform, r.topics, r.buildTier, r.appVersion))
}
