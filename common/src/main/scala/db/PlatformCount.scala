package db

import play.api.libs.json.{Format, Json}

case class PlatformCount(
  total: Int,
  ios: Int,
  android: Int,
  newsstand: Int
)

object PlatformCount {
  implicit val platformCountJF: Format[PlatformCount] = Json.format[PlatformCount]
}
