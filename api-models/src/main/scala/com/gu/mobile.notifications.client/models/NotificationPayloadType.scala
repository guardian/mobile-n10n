package com.gu.mobile.notifications.client.models

import play.api.libs.json._

sealed trait NotificationPayloadType

object NotificationPayloadType {

  case object BreakingNews extends NotificationPayloadType {
    override def toString = "news"
  }

  case object ContentAlert extends NotificationPayloadType {
    override def toString = "content"
  }

  case object FootballMatchStatus extends NotificationPayloadType {
    override def toString = "football-match-status"
  }

  implicit val jf = new Writes[NotificationPayloadType] {
    override def writes(nType: NotificationPayloadType): JsValue = JsString(nType.toString)
  }
}