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
      assert(queryString.contains("AND partition_date = '2022-09-28'"))
    }

    "include a two partitionDates if just before midnight" in new Scope {
      val notificationId = "1234-ASDF"
      val sentTime = LocalDateTime.of(2022, 9, 28, 23, 58, 0)
      val queryString = SloMonitor.generateQueryString(notificationId, sentTime)
      assert(queryString.contains("AND (partition_date = '2022-09-28' OR partition_date = '2022-09-29')"))
    }
  }
}
