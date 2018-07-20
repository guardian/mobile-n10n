package models

import play.api.libs.json.{Format, Json}

case class Registration(deviceToken: String, platform: Platform, topics: Set[Topic], buildTier: Option[String])

object Registration {
  implicit val registrationJF: Format[Registration] = Json.format[Registration]
}