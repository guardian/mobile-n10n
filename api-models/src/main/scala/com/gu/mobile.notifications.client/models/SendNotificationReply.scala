package com.gu.mobile.notifications.client.models

import play.api.libs.json.{Json, OFormat}

/** Acknowledgement of notification with a message ID for looking up statistics on that message */
case class SendNotificationReply(messageId: String)

object SendNotificationReply {
  implicit val jf: OFormat[SendNotificationReply] = Json.format[SendNotificationReply]
}