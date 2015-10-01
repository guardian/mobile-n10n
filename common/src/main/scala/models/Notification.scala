package models

import play.api.libs.json.Json

object Notification {
  implicit val jf = Json.format[Notification]
}

case class Notification(
  uuid: String,
  sender: String,
  timeToLiveInSeconds: Int,
  payload: MessagePayload
)
