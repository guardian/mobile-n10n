package com.gu.notifications.events.model

import play.api.libs.json.Json

case class PlatformCount(
  total: Int,
  ios: Int,
  android: Int
)


object PlatformCount {

  val empty: PlatformCount = PlatformCount(0, 0, 0)

  def from(platform: Platform): PlatformCount = platform match {
    case Ios => PlatformCount(1, 1, 0)
    case Android => PlatformCount(1, 0, 1)
  }

  def combine(countsA: PlatformCount, countsB: PlatformCount): PlatformCount = PlatformCount(
    total = countsA.total + countsB.total,
    ios = countsA.ios + countsB.ios,
    android = countsA.android + countsB.android
  )
  implicit val jf = Json.format[PlatformCount]
}




