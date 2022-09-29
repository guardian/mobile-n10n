package com.gu.notifications.slos

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import java.time.LocalDateTime

class SloMonitorSpec extends Specification {
  "generateQueryString" should {
    "include a single partitionDate if before midnight" in new Scope {
      val notificationId = "1234-ASDF"
      val sentTime = LocalDateTime.of(2022, 9, 28, 9, 30, 0)
      val queryString = SloMonitor.generateQueryString(notificationId, sentTime)

      queryString shouldEqual s"""
         |SELECT COUNT(*)
         |FROM notification_received_test
         |WHERE notificationid = '$notificationId'
         |AND partition_date = '2022-09-28'
         |AND DATE_DIFF('second', from_iso8601_timestamp('2022-09-28T09:30'), received_timestamp) < 120
      """.stripMargin
    }

    "include a two partitionDates if just before midnight" in new Scope {
      val notificationId = "1234-ASDF"
      val sentTime = LocalDateTime.of(2022, 9, 28, 23, 58, 0)
      val queryString = SloMonitor.generateQueryString(notificationId, sentTime)

      queryString shouldEqual
        s"""
           |SELECT COUNT(*)
           |FROM notification_received_test
           |WHERE notificationid = '$notificationId'
           |AND (partition_date = '2022-09-28' OR partition_date = '2022-09-29')
           |AND DATE_DIFF('second', from_iso8601_timestamp('2022-09-28T23:58'), received_timestamp) < 120
      """.stripMargin
    }
  }
}
