package com.gu.notifications.events

import java.time.LocalDateTime

import com.gu.notifications.events.model.TenSecondUnit
import play.api.libs.json._

case class RawEvent(
  queryString: String,
  event: String,
  dateTime: LocalDateTime
)

object RawEvent {
  implicit val rawEventJF: Reads[RawEvent] = Json.reads[RawEvent].map(rawEvent => rawEvent.copy(dateTime =  rawEvent.dateTime.truncatedTo(TenSecondUnit)))
}
