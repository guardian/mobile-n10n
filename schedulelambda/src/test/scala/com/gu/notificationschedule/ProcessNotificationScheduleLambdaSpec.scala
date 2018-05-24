package com.gu.notificationschedule

import java.time.Instant

import com.amazonaws.services.cloudwatch.model.StandardUnit
import com.gu.notificationschedule.cloudwatch.{CloudWatch, Timer}
import com.gu.notificationschedule.dynamo.{NotificationSchedulePersistence, NotificationsScheduleEntry}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class ProcessNotificationScheduleLambdaSpec extends Specification with Mockito{
  "ProcessNotificationScheduleLambda" should {
    "send metrics" in {
      var sentMetricsCalled = false
      val cloudWatch = new CloudWatch {
        override def sendMetricsSoFar(): Unit = sentMetricsCalled = true

        override def queueMetric(metricName: String, value: Double, standardUnit: StandardUnit, instant: Instant): Boolean = ???

        override def startTimer(metricName: String): Timer = ???

        override def meterHttpStatusResponses(metricName: String, code: Int): Unit = ???
      }

      val processNotificationScheduleLambda = new ProcessNotificationScheduleLambda(NotificationScheduleConfig("test-app", "test-stage", "test-stack"), cloudWatch, new NotificationSchedulePersistence {
        override def query(): Seq[NotificationsScheduleEntry] = List()

        override def write(notificationsScheduleEntry: NotificationsScheduleEntry, sent: Boolean, sent_epoch_s: Long): Unit = ()
      }
      )
      processNotificationScheduleLambda.apply()

      sentMetricsCalled must beTrue
    }
  }
}
