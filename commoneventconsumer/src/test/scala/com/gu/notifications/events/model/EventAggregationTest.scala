package com.gu.notifications.events.model

import org.specs2.mutable.Specification
import java.time.LocalDateTime

class EventAggregationTest extends Specification {

  val mockPlatformCount = PlatformCount(10, 10, 10)
  val mockProviderCount = ProviderCount(10, mockPlatformCount, mockPlatformCount)
  def mockEventAggregation(timing: Map[LocalDateTime, Int]) = EventAggregation(mockPlatformCount, mockProviderCount, timing)

  "timingPercentiles" should {
    "return None if no timing" in {
      mockEventAggregation(Map.empty).timingPercentiles should equalTo(None)
    }
    "return return all deciles plus 95th and 99th percentiles" in {
      val now = LocalDateTime.now
      val timings = Map(
        now.plusSeconds(1) -> 12,
        now.plusSeconds(2) -> 68,
        now.plusSeconds(10) -> 20
      )
      val expected = Some(
        TimingPercentiles(
          now.plusSeconds(1),
          now.plusSeconds(2),
          now.plusSeconds(2),
          now.plusSeconds(2),
          now.plusSeconds(2),
          now.plusSeconds(2),
          now.plusSeconds(2),
          now.plusSeconds(10),
          now.plusSeconds(10),
          now.plusSeconds(10),
          now.plusSeconds(10)
        )
      )
      mockEventAggregation(timings).timingPercentiles should equalTo(expected)
    }
  }

}
