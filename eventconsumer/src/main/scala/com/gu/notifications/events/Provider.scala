package com.gu.notifications.events

sealed trait Provider

case object Azure extends Provider
case object Fcm extends Provider

object Provider {
  def fromString(providerString: String): Option[Provider] = providerString.toLowerCase match {
    case "azure" => Some(Azure)
    case "fcm" => Some(Fcm)
    case _ => None
  }
}
