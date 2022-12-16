package com.gu.notifications.worker.models

import org.specs2.matcher.Matchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class SendingResultsSpec extends Specification with Matchers {

  "LatencyMetrics.aggregateForCloudWatch" should {
    "Create a single batch of token delivery latencies if there are less than 150 unique values" in new Scope {
      val allDeliveries = List(1L,1L,1L,1L,2L,2L,2L,3L)
      val result = LatencyMetrics.aggregateForCloudWatch(allDeliveries)
      val expected = List(LatencyMetricsForCloudWatch(
        uniqueValues = List(1L, 2L, 3L),
        orderedCounts = List(4, 3, 1),
      ))
      result shouldEqual(expected)
    }

    "Create multiple batches of token delivery latencies if there are more than 150 unique values" in new Scope {
      val threeHundredUniques = (1L to 300L).toList
      val moreData = List(1L, 1L, 1L, 1L, 300L, 300L, 300L)
      val allDeliveries = threeHundredUniques ++ moreData
      val result = LatencyMetrics.aggregateForCloudWatch(allDeliveries)
      val expected = List(
        LatencyMetricsForCloudWatch(
          uniqueValues = (1L to 150L).toList,
          orderedCounts = List(5) ++ List.fill(149)(1),
        ),
        LatencyMetricsForCloudWatch(
          uniqueValues = (151L to 300L).toList,
          orderedCounts = List.fill(149)(1) ++ List(4),
        )
      )
      result shouldEqual (expected)
    }

  }

}
