package com.gu.notifications.events.model

import play.api.libs.json.Json


case class ProviderCount(
  total: Int,
  azure: PlatformCount,
  firebase: PlatformCount
)


object ProviderCount {
  def from(provider: Provider, platform: Platform): ProviderCount = (provider, platform) match {
    case (Azure, Ios) => ProviderCount(1, PlatformCount(1, 1, 0), PlatformCount(0, 0, 0))
    case (Azure, Android) => ProviderCount(1, PlatformCount(1, 0, 1), PlatformCount(0, 0, 0))
    case (Fcm, Ios) => ProviderCount(1, PlatformCount(0, 0, 0), PlatformCount(1, 1, 0))
    case (Fcm, Android) => ProviderCount(1, PlatformCount(0, 0, 0), PlatformCount(1, 0, 1))
  }

  def combine(countsA: ProviderCount, countsB: ProviderCount): ProviderCount = ProviderCount(
    total = countsA.total + countsB.total,
    azure = PlatformCount.combine(countsA.azure, countsB.azure),
    firebase = PlatformCount.combine(countsA.firebase, countsB.firebase)
  )
  implicit val jf = Json.format[ProviderCount]
}
