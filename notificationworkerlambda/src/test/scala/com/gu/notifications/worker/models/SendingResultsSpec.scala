package com.gu.notifications.worker.models

import org.specs2.matcher.Matchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class SendingResultsSpec extends Specification with Matchers {

  "LatencyMetrics.aggregateForCloudWatch" should {
    "Batch token delivery latencies into the correct format" in new Scope {
      val allDeliveries = List(1L,1L,1L,1L,2L,2L,2L,3L)
      val result = LatencyMetrics.aggregateForCloudWatch(allDeliveries)
      val expected = LatencyMetricsForCloudWatch(
        uniqueValues = List(1L, 2L, 3L),
        orderedCounts = List(4, 3, 1),
      )
      result shouldEqual(expected)
    }
  }

}
