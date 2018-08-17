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

  /*implicit val dateTimeFormat: Reads[LocalDateTin] = new Reads[ZonedDateTime] {
    override def reads(json: JsValue): JsResult[ZonedDateTime] = json match {
      case JsString(dateString) => JsSuccess(ZonedDateTime.parse(dateString, dateTimeFormatter))
      case _ => JsError("Invalid data type")
    }
  }*/
  implicit val rawEventJF: Reads[RawEvent] = Json.reads[RawEvent]
}
