package com.gu.notifications.events.model

import play.api.libs.json.{Json, OFormat}

case class PlatformCount(
  total: Int,
  ios: Int,
  android: Int,
  iosEdition: Option[Int],
  androidEdition: Option[Int]
)


object PlatformCount {
  implicit val jf: OFormat[PlatformCount] = Json.format[PlatformCount]
}




