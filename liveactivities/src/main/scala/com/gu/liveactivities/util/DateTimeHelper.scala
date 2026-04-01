package com.gu.liveactivities.util

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object DateTimeHelper {
  val iso8601formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZZ")

  def dateTimeToString(dt: ZonedDateTime): String = dt.format(iso8601formatter)

  def dateTimeFromString(s: String): ZonedDateTime = ZonedDateTime.parse(s, iso8601formatter)
}
