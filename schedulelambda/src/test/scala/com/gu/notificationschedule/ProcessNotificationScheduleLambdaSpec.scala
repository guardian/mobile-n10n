package com.gu.notificationschedule

import java.time.{Clock, Instant, ZoneOffset}
import java.util.UUID

import com.amazonaws.services.cloudwatch.model.StandardUnit
import com.gu.notificationschedule.cloudwatch.{CloudWatch, Timer}
import com.gu.notificationschedule.dynamo.{NotificationSchedulePersistenceSync, NotificationsScheduleEntry}
import com.gu.notificationschedule.external.SsmConfig
import com.typesafe.config.ConfigFactory
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.collection.JavaConverters._
import scala.util.Success

class ProcessNotificationScheduleLambdaSpec extends Specification with Mockito {
  "ProcessNotificationScheduleLambda" should {
    "send metrics" in {
      var sentMetricsCalled = false
      val cloudWatch = new CloudWatch {
        override def sendMetricsSoFar(): Unit = sentMetricsCalled = true

        override def queueMetric(metricName: String, value: Double, standardUnit: StandardUnit, instant: Instant): Boolean = true

        override def startTimer(metricName: String): Timer = mock[Timer]

        override def meterHttpStatusResponses(metricName: String, code: Int): Unit = ???
      }

      val config = ConfigFactory.parseMap(Map(
        "schedule.notifications.pushTopicUrl" -> "http://push.topic.invalid",
        "schedule.notifications.secretKey" -> "secretkey"
      ).asJava)
      val processNotificationScheduleLambda = new ProcessNotificationScheduleLambda(
        new NotificationScheduleConfig(SsmConfig("test-app", "test-stage", "test-stack", config)),
        cloudWatch,
        new NotificationSchedulePersistenceSync {
          override def querySync(): Seq[NotificationsScheduleEntry] = List()

          override def writeSync(notificationsScheduleEntry: NotificationsScheduleEntry, maybeSent: Option[Long]): Unit = ()
        }, (nowEpoch: Long, notificationsScheduleEntry: NotificationsScheduleEntry) => Success(()),
        Clock.systemUTC()

      )
      processNotificationScheduleLambda.apply()

      sentMetricsCalled must beTrue
    }

    "request and send notifications" in {
      var sentMetricsCalled = false
      val now = Instant.now
      val clock = Clock.fixed(now, ZoneOffset.UTC)
      val cloudWatch = new CloudWatch {
        override def sendMetricsSoFar(): Unit = sentMetricsCalled = true

        override def queueMetric(metricName: String, value: Double, standardUnit: StandardUnit, instant: Instant): Boolean = true

        override def startTimer(metricName: String): Timer = mock[Timer]

        override def meterHttpStatusResponses(metricName: String, code: Int): Unit = ???
      }

      val config = ConfigFactory.parseMap(Map(
        "schedule.notifications.pushTopicUrl" -> "http://push.topic.invalid",
        "schedule.notifications.secretKey" -> "secretkey"
      ).asJava)
      val testNotificationScheduleEntry = NotificationsScheduleEntry(UUID.randomUUID().toString, "notification", 1, 1)
      val processNotificationScheduleLambda = new ProcessNotificationScheduleLambda(
        new NotificationScheduleConfig(SsmConfig("test-app", "test-stage", "test-stack", config)),
        cloudWatch,
        new NotificationSchedulePersistenceSync {
          override def querySync(): Seq[NotificationsScheduleEntry] = {

            List(testNotificationScheduleEntry)
          }

          override def writeSync(notificationsScheduleEntry: NotificationsScheduleEntry, maybeSent: Option[Long]): Unit = ()
        }, (nowEpoch: Long, notificationsScheduleEntry: NotificationsScheduleEntry) => {
          notificationsScheduleEntry must beEqualTo( testNotificationScheduleEntry)
          nowEpoch must beEqualTo(now.getEpochSecond)
          Success(())
        },
        clock
      )
      processNotificationScheduleLambda.apply()
      ok
    }
  }
}
