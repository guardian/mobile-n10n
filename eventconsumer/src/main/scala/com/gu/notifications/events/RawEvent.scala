package com.gu.notifications.events

import java.time.LocalDateTime

import play.api.libs.json._

case class RawEvent(
  queryString: String,
  event: String,
  dateTime: LocalDateTime
)

object RawEvent {
  implicit val rawEventJF: Reads[RawEvent] = Json.reads[RawEvent]
}
