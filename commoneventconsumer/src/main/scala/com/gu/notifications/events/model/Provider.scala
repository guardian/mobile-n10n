package com.gu.notifications.events.model

sealed trait Provider

case object Azure extends Provider
case object Fcm extends Provider
case object Guardian extends Provider

object Provider {
  def fromString(providerString: String): Option[Provider] = providerString.toLowerCase match {
    case "azure" => Some(Azure)
    case "fcm" => Some(Fcm)
    case "guardian" => Some(Guardian)
    case _ => None
  }

}
