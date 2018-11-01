package com.gu.notifications.events.model

import play.api.libs.json._


case class ProviderCount(
  total: Int,
  azure: PlatformCount = PlatformCount.empty,
  firebase: PlatformCount = PlatformCount.empty,
  guardian: PlatformCount = PlatformCount.empty
)


object ProviderCount {

  def from(provider: Provider, platform: Platform): ProviderCount = (provider, platform) match {
    case (Azure, Ios) => ProviderCount(total = 1, azure = PlatformCount(1, 1, 0))
    case (Azure, Android) => ProviderCount(total = 1, azure = PlatformCount(1, 0, 1))
    case (Fcm, Ios) => ProviderCount(total = 1, firebase = PlatformCount(1, 1, 0))
    case (Fcm, Android) => ProviderCount(total = 1, firebase = PlatformCount(1, 0, 1))
    case (Guardian, Ios) => ProviderCount(total = 1, guardian = PlatformCount(1, 1, 0))
    case (Guardian, Android) => ProviderCount(total = 1, guardian = PlatformCount(1, 0, 1))
  }

  def combine(countsA: ProviderCount, countsB: ProviderCount): ProviderCount = ProviderCount(
    total = countsA.total + countsB.total,
    azure = PlatformCount.combine(countsA.azure, countsB.azure),
    firebase = PlatformCount.combine(countsA.firebase, countsB.firebase),
    guardian = PlatformCount.combine(countsA.guardian, countsB.guardian)
  )
  implicit val providerCountJF: Format[ProviderCount] = new Format[ProviderCount] {
    val defaultWrites = Json.writes[ProviderCount]
    override def writes(providerCount: ProviderCount): JsValue = defaultWrites.writes(providerCount)
    override def reads(json: JsValue): JsResult[ProviderCount] = json match {
      case JsObject(attributes) => for {
        total <- attributes("total").validate[Int]
        azure <- attributes.get("azure").map(_.validate[PlatformCount]).getOrElse(JsSuccess(PlatformCount.empty))
        firebase <- attributes.get("firebase").map(_.validate[PlatformCount]).getOrElse(JsSuccess(PlatformCount.empty))
        guardian <- attributes.get("guardian").map(_.validate[PlatformCount]).getOrElse(JsSuccess(PlatformCount.empty))
      } yield ProviderCount(
        total = total,
        azure = azure,
        firebase = firebase,
        guardian = guardian
      )
      case _ => JsError("Was expecting a JsObject in order to parse a ProviderCount")
    }
  }
}
