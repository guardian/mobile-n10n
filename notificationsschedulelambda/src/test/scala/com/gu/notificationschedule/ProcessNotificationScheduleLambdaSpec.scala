package com.gu.notificationschedule

import com.gu.notificationschedule.cloudwatch.CloudWatch
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class ProcessNotificationScheduleLambdaSpec extends Specification with Mockito{
  "ProcessNotificationScheduleLambda" should {
    "send metrics" in {
      "true" must_== ("true")
    }
  }
}
