package models

import java.util.UUID
import play.api.libs.json.Json

object Notification {
  implicit val jf = Json.format[Notification]
}

case class Notification(
  uuid: UUID,
  sender: String,
  timeToLiveInSeconds: Int
)
