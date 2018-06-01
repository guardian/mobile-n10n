package com.gu.notificationschedule

import java.time.Instant

import com.amazonaws.services.cloudwatch.model.StandardUnit
import com.gu.notificationschedule.cloudwatch.{CloudWatch, Timer}
import com.gu.notificationschedule.dynamo.{NotificationSchedulePersistenceSync, NotificationsScheduleEntry, ScheduleTableConfig}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class ProcessNotificationScheduleLambdaSpec extends Specification with Mockito{
  "ProcessNotificationScheduleLambda" should {
    "send metrics" in {
      var sentMetricsCalled = false
      val cloudWatch = new CloudWatch {
        override def sendMetricsSoFar(): Unit = sentMetricsCalled = true

        override def queueMetric(metricName: String, value: Double, standardUnit: StandardUnit, instant: Instant): Boolean = true

        override def startTimer(metricName: String): Timer = mock[Timer]

        override def meterHttpStatusResponses(metricName: String, code: Int): Unit = ???
      }

      val processNotificationScheduleLambda = new ProcessNotificationScheduleLambda(ScheduleTableConfig("test-app", "test-stage", "test-stack"), cloudWatch, new NotificationSchedulePersistenceSync {
        override def querySync(): Seq[NotificationsScheduleEntry] = List()
        override def writeSync(notificationsScheduleEntry: NotificationsScheduleEntry, maybeEpochSentS: Option[Long]): Unit = ()
      }
      )
      processNotificationScheduleLambda.apply()

      sentMetricsCalled must beTrue
    }
  }
}
