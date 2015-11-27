package models

import java.util.UUID

import play.api.libs.json.Json

case class PushResult(id: UUID)

object PushResult {
  import JsonUtils._
  implicit val jf = Json.format[PushResult]
}
