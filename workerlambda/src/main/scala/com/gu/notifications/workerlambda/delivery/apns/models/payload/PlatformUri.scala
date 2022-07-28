package com.gu.notifications.workerlambda.delivery.apns.models.payload

case class PlatformUri(uri: String, `type`: PlatformUriType)

sealed trait PlatformUriType
object PlatformUriTypes {
  case object Item extends PlatformUriType { override def toString: String = "item" }
  case object FootballMatch extends PlatformUriType { override def toString: String = "football-match" }
  case object External extends PlatformUriType { override def toString: String = "external" }
}
