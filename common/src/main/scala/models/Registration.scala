package models

import play.api.libs.json._

case class Registration(
  deviceToken: DeviceToken,
  platform: Platform,
  topics: Set[Topic],
  buildTier: Option[String],
  appVersion: Option[String]
)

object Registration {
  implicit val registrationJF: Format[Registration] = Json.format[Registration]
}
