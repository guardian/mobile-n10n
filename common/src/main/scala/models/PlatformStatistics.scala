package models

import JsonUtils._

case class PlatformStatistics(platform: Platform, recipientsCount: Int)

object PlatformStatistics {
  import play.api.libs.json._

  implicit val jf = Json.format[PlatformStatistics]
}
