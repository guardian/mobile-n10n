package com.gu.notificationschedule.notifications

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

import com.amazonaws.services.cloudwatch.model.StandardUnit
import com.gu.notificationschedule.NotificationScheduleConfig
import com.gu.notificationschedule.cloudwatch.{CloudWatchMetrics, Timer}
import com.gu.notificationschedule.dynamo.NotificationsScheduleEntry
import com.gu.notificationschedule.external.SsmConfig
import com.typesafe.config.ConfigFactory
import okhttp3._
import okio.Buffer
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.jdk.CollectionConverters._
import scala.util.Success

class RequestNotificationImplSpec extends Specification with Mockito {
  "RequestNotificationImpl" should {
    val config = ConfigFactory.parseMap(Map(
      "schedule.notifications.pushTopicUrl" -> "http://push.topic.invalid",
      "schedule.notifications.secretKey" -> "secretkey"
    ).asJava)
    val notificationsScheduleEntry = NotificationsScheduleEntry(UUID.randomUUID().toString, "notification", 1, 1)
    val cloudWatchMetrics = new CloudWatchMetrics {
      override def queueMetric(metricName: String, value: Double, standardUnit: StandardUnit, instant: Instant): Boolean = ???

      override def startTimer(metricName: String): Timer = mock[Timer]

      override def meterHttpStatusResponses(metricName: String, code: Int): Unit = ???
    }

    "send notification request to notifications" in {
      val okHttpClient = mock[OkHttpClient]
      val mockCall = mock[Call]
      okHttpClient.newCall(any[Request]()) answers {
        (_: Any) match {
          case (request: Request) => {
            request.url() must beEqualTo(HttpUrl.parse("http://push.topic.invalid"))
            request.header("Authorization") must beEqualTo(s"Bearer secretkey")
            request.method().toLowerCase must beEqualTo("post")
            val buffer = new Buffer()
            request.body().writeTo(buffer)
            new String(buffer.readByteArray(), StandardCharsets.UTF_8) must beEqualTo("notification")
            request.body().contentType().toString must beEqualTo("application/json; charset=utf-8")
            mockCall.execute() returns new Response.Builder().code(200)
              .protocol(Protocol.HTTP_2)
              .request(request)
              .message("status message")
              .headers(Headers.of(Map[String, String]().asJava))
              .body(ResponseBody.create(MediaType.parse("application/json"), ""))
              .build()
            mockCall
          }
        }
      }
      val requestNotification = new RequestNotificationImpl(
        new NotificationScheduleConfig(SsmConfig("app", "stack", "stage", config)), okHttpClient,
        cloudWatchMetrics)
      requestNotification(1, notificationsScheduleEntry) must beEqualTo(Success(()))
    }
    "handle bad status" in {
      val okHttpClient = mock[OkHttpClient]
      val mockCall = mock[Call]
      okHttpClient.newCall(any[Request]()) answers {
        (_: Any) match {
          case (request: Request) => {
            mockCall.execute() returns new Response.Builder().code(400)
              .protocol(Protocol.HTTP_2)
              .request(request)
              .message("status message")
              .headers(Headers.of(Map[String, String]().asJava))
              .build()
            mockCall
          }
        }
      }
      val requestNotification = new RequestNotificationImpl(
        new NotificationScheduleConfig(SsmConfig("app", "stack", "stage", config)), okHttpClient,
        cloudWatchMetrics)
      requestNotification(1, notificationsScheduleEntry).get must throwA[RequestNotificationException]
    }
    "handle no response" in {
      val okHttpClient = mock[OkHttpClient]
      val mockCall = mock[Call]
      okHttpClient.newCall(any[Request]()) answers {
        (_: Any) match {
          case (request: Request) => mockCall

        }
      }
      val requestNofication = new RequestNotificationImpl(
        new NotificationScheduleConfig(SsmConfig("app", "stack", "stage", config)), okHttpClient,
        cloudWatchMetrics)
      requestNofication(1, notificationsScheduleEntry).get must throwA[RequestNotificationException]
    }
    "handle error" in {
      val okHttpClient = mock[OkHttpClient]
      val mockCall = mock[Call]
      val exception = new NullPointerException
      okHttpClient.newCall(any[Request]()) answers {
        (_: Any) match {
          case (request: Request) => {

            mockCall.execute() throws exception
            mockCall
          }

        }
      }
      val requestNotification = new RequestNotificationImpl(
        new NotificationScheduleConfig(SsmConfig("app", "stack", "stage", config)), okHttpClient,
        cloudWatchMetrics)
      requestNotification(1, notificationsScheduleEntry).get must throwA(exception)
    }
  }
}
