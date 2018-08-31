package com.gu.notifications.events

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import play.api.libs.json._

case class RawEvent(
  queryString: String,
  event: String,
  dateTime: LocalDateTime
)

object RawEvent {

  val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
  implicit val rawEventJF: Reads[RawEvent] = Json.reads[RawEvent]
}
