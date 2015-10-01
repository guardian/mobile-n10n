package models

import play.api.libs.json.Json

object MessagePayload {
  implicit val jf = Json.format[MessagePayload]
}

case class MessagePayload(
  link: Option[String],
  `type`: Option[String],
  ticker: Option[String],
  title: Option[String],
  message: Option[String]
)
