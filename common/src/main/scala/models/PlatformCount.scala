package models

import play.api.libs.json.{Format, Json}

case class PlatformCount(
  total: Int,
  ios: Int,
  android: Int,
  newsstand: Int
) {
  def get(platform: Platform): Int = platform match {
    case Android => android
    case Newsstand => newsstand
    case _ => ios
  }
}

object PlatformCount {
  implicit val platformCountJF: Format[PlatformCount] = Json.format[PlatformCount]
}
