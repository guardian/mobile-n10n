package com.gu.liveactivities.util

import org.specs2.mutable.Specification
import java.time.ZonedDateTime

class DateTimeHelperSpec extends Specification {
  "DateTimeHelper" should {
    "round-trip a date-time string to long and back without loss" in {
      val originalString = "2026-04-28T15:30:45.123+0000"
      val zonedDateTime = DateTimeHelper.dateTimeFromString(originalString)
      val asLong = DateTimeHelper.dateTimeToLong(zonedDateTime)
      val fromLong = DateTimeHelper.dateTimeFromLong(asLong)
      val backToString = DateTimeHelper.dateTimeToString(fromLong)
      val backToZoned = DateTimeHelper.dateTimeFromString(backToString)

      // The string may not be exactly the same due to formatting, but the instant should match
      zonedDateTime.toInstant mustEqual fromLong.toInstant
      backToZoned.toInstant mustEqual zonedDateTime.toInstant
      backToString mustEqual originalString
    }
  }
}
