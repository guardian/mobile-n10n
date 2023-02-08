package com.gu.notifications.worker.models

import org.specs2.matcher.Matchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class SendingResultsSpec extends Specification with Matchers {

  "LatencyMetrics.aggregateForCloudWatch" should {
    "Create a single batch of token delivery latencies if there are less than 150 unique values (and the default batch size is used)" in new Scope {
      val allDeliveries = List(1L,1L,1L,1L,2L,2L,2L,3L)
      val result = LatencyMetrics.aggregateForCloudWatch(allDeliveries)
      val expected = List(LatencyMetricsForCloudWatch(
        uniqueValues = List(1L, 2L, 3L),
        orderedCounts = List(4, 3, 1),
      ))
      result shouldEqual expected
    }

    "Create multiple batches of token delivery latencies if the number of items exceeds the batch size" in new Scope {
      val allDeliveries = List(7L, 5L, 7L, 6L, 6L, 6L, 6L, 10L, 10L, 10L, 10L, 10L, 9L, 4L, 4L, 4L, 4L, 4L, 4L, 9L, 9L)
      val result = LatencyMetrics.aggregateForCloudWatch(allDeliveries, batchSize = 3)
      val expected = List(
        LatencyMetricsForCloudWatch(
          uniqueValues = List(7L, 5L, 6L),
          orderedCounts = List(2, 1, 4),
        ),
        LatencyMetricsForCloudWatch(
          uniqueValues = List(10L, 9L, 4L),
          orderedCounts = List(5, 3, 6),
        )
      )
      result shouldEqual expected
    }

    "Create multiple batches of 150 by default" in new Scope {
      val sixHundredUniques = (1L to 600L).toList
      val result = LatencyMetrics.aggregateForCloudWatch(sixHundredUniques)
      result.map(_.uniqueValues.size) shouldEqual List(150, 150, 150, 150)
    }

  }

  "LatencyMetrics.audienceSizeBucket" should {

    "Categorise a small audience correctly" in new Scope {
      LatencyMetrics.audienceSizeBucket(Some(190007)) shouldEqual "S"
    }

    "Categorise a medium audience correctly" in new Scope {
      LatencyMetrics.audienceSizeBucket(Some(858927)) shouldEqual "M"
    }

    "Categorise a large audience correctly" in new Scope {
      LatencyMetrics.audienceSizeBucket(Some(1425749)) shouldEqual "L"
    }

    "Categorise an extra large audience correctly" in new Scope {
      LatencyMetrics.audienceSizeBucket(Some(3012022)) shouldEqual "XL"
    }

    "Mark a missing audience as unknown" in new Scope {
      LatencyMetrics.audienceSizeBucket(None) shouldEqual "unknown"
    }

  }


}
