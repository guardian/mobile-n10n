package com.gu.notifications.events.model

import play.api.libs.json.Json

case class PlatformCount(
  total: Int,
  ios: Int,
  android: Int,
  iosEdition: Option[Int],
  androidEdition: Option[Int]
)


object PlatformCount {
  implicit val jf = Json.format[PlatformCount]
}




