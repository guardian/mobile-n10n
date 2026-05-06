package com.gu.liveactivities.util

import java.time.ZonedDateTime
import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import play.api.libs.json.JsPath
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import play.api.libs.json.Format
import org.scanamo.DynamoFormat
import org.scanamo.generic.semiauto._

object DateTimeHelper {
  val iso8601formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZZ")

  def dateTimeToString(dt: ZonedDateTime): String = dt.format(iso8601formatter)

  def dateTimeFromString(s: String): ZonedDateTime = ZonedDateTime.parse(s, iso8601formatter)

  def dateTimeToLong(dt: ZonedDateTime): Long = dt.toInstant.toEpochMilli
  def dateTimeFromLong(l: Long): ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(l), ZoneOffset.UTC)

  implicit val zonedDateTimeRead: Reads[ZonedDateTime] = JsPath.read[String].map(dateTimeFromString)

  implicit val zonedDateTimeWrite: Writes[ZonedDateTime] = implicitly[Writes[String]].contramap(dateTimeToString)

  implicit val zonedDateTimeJsonFormat: Format[ZonedDateTime] =
    Format(zonedDateTimeRead, zonedDateTimeWrite)

  implicit val zonedDateTimeDynamoFormat: DynamoFormat[ZonedDateTime] = DynamoFormat.coercedXmap[ZonedDateTime, String, IllegalArgumentException](
    dateTimeFromString,
    dateTimeToString
  )    
}
