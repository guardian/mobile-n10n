package com.gu.liveactivities.util

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import play.api.libs.json.JsPath
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import play.api.libs.json.Format

object DateTimeHelper {
  val iso8601formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZZ")

  def dateTimeToString(dt: ZonedDateTime): String = dt.format(iso8601formatter)

  def dateTimeFromString(s: String): ZonedDateTime = ZonedDateTime.parse(s, iso8601formatter)

  implicit val zonedDateTimeRead: Reads[ZonedDateTime] = JsPath.read[String].map(dateTimeFromString)

  implicit val zonedDateTimeWrite: Writes[ZonedDateTime] = implicitly[Writes[String]].contramap(dateTimeToString)

  implicit val zonedDateTimeFormat: Format[ZonedDateTime] =
    Format(zonedDateTimeRead, zonedDateTimeWrite)
}
