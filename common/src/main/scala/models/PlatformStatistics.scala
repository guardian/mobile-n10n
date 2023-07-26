package models


case class PlatformStatistics(platform: Platform, recipientsCount: Int)

object PlatformStatistics {
  import play.api.libs.json._

  implicit val jf: OFormat[PlatformStatistics] = Json.format[PlatformStatistics]
}
