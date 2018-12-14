package com.gu.notifications.events.model

import play.api.libs.json._
case class RawEvent(
  queryString: String,
  event: String
)

object RawEvent {
  implicit val rawEventJF: Reads[RawEvent] = Json.reads[RawEvent]
}
