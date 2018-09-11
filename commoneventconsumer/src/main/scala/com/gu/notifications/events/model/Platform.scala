package com.gu.notifications.events.model

import play.api.libs.json.Json

sealed trait Platform

case object Ios extends Platform
case object Android extends Platform

object Platform {
  def fromString(platformString: String): Option[Platform] = platformString.toLowerCase match {
    case "ios" => Some(Ios)
    case "android" => Some(Android)
    case _ => None
  }
}