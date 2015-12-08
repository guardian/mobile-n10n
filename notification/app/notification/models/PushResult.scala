package notification.models

import java.util.UUID

import play.api.libs.json.Json

case class PushResult(id: UUID)

object PushResult {
  implicit val jf = Json.format[PushResult]
}
