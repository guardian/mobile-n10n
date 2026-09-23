package com.gu.notifications.worker.delivery

import cats.effect.{ContextShift, IO, Timer}
import com.google.firebase.messaging.AndroidConfig
import com.gu.notifications.worker.delivery.DeliveryException._
import com.turo.pushy.apns.PushType
import models.Importance.Major
import models.Link.Internal
import models.TopicTypes.Breaking
import models.{BreakingNewsNotification, GITContent, Notification, NotificationType, Topic}
import org.specs2.mutable.Specification

import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class DeliveryServiceSpec extends Specification {
  "DeliveryService" should {
    "not retry a FCM failed request (e.g. FCM server error)" in {
      implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global
      implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
      implicit val timer: Timer[IO] = IO.timer(executionContext)

      val client = new FailedRequestClient
      val service = new DeliveryServiceImpl[IO, FailedRequestClient](client)

      val result = service.send(notification, "token").compile.toList.unsafeRunSync()

      client.attempts.get shouldEqual 1
      result must contain(beLeft[DeliveryException].like {
        case _: FailedFCMRequest => ok
      })
    }

    "retry a transient failed APNS request" in {
      implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global
      implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
      implicit val timer: Timer[IO] = IO.timer(executionContext)

      val client = new TransientFailureClient
      val service = new DeliveryServiceImpl[IO, TransientFailureClient](client)

      val result = service.send(notification, "token").compile.toList.unsafeRunSync()

      client.attempts.get shouldEqual 2
      result.count(_.isRight) shouldEqual 1
    }

    "not retry a failed APNS request caused by a client timeout" in {
      implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global
      implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
      implicit val timer: Timer[IO] = IO.timer(executionContext)

      val client = new ClientTimeoutFailureClient
      val service = new DeliveryServiceImpl[IO, ClientTimeoutFailureClient](client)

      val result = service.send(notification, "token").compile.toList.unsafeRunSync()

      client.attempts.get shouldEqual 1
      result must contain(beLeft[DeliveryException].like {
        case failure: FailedAPNSRequest => failure.errorCode shouldEqual Some("ClientTimeout")
      })
    }


    "retry a failed APNS delivery" in {
      implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global
      implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
      implicit val timer: Timer[IO] = IO.timer(executionContext)

      val client = new FailedAPNSDeliveryClient
      val service = new DeliveryServiceImpl[IO, FailedAPNSDeliveryClient](client)

      val result = service.send(notification, "token").compile.toList.unsafeRunSync()

      client.attempts.get shouldEqual 2
      result.count(_.isRight) shouldEqual 1
  }
  }


  private val notification = BreakingNewsNotification(
    id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
    `type` = NotificationType.BreakingNews,
    title = Some("Test notification"),
    message = Some("Test notification"),
    thumbnailUrl = None,
    sender = "test@example.com",
    link = Internal("test", None, GITContent, None),
    imageUrl = None,
    importance = Major,
    topic = List(Topic(Breaking, "uk")),
    dryRun = None
  )

  private class FailedRequestClient extends DeliveryClient {
    type Success = FcmDeliverySuccess
    type Payload = FcmPayload

    val attempts = new AtomicInteger(0)
    val dryRun = false
    val payloadBuilder: Notification => Option[FcmPayload] =
      _ => Some(FcmPayload(AndroidConfig.builder().build()))

    def sendNotification(notificationId: UUID, token: String, payload: FcmPayload, dryRun: Boolean)
                        (onComplete: Either[DeliveryException, FcmDeliverySuccess] => Unit)
                        (implicit executionContext: ExecutionContextExecutor): Unit = {
      attempts.incrementAndGet()
      onComplete(Left(FailedFCMRequest(notificationId, token, new RuntimeException("server error"))))
    }
  }

  private class TransientFailureClient extends DeliveryClient {
    type Success = ApnsDeliverySuccess
    type Payload = ApnsPayload

    val attempts = new AtomicInteger(0)
    val dryRun = false
    val payloadBuilder: Notification => Option[ApnsPayload] =
      _ => Some(ApnsPayload("{}", None, None, PushType.ALERT))

    def sendNotification(notificationId: UUID, token: String, payload: ApnsPayload, dryRun: Boolean)
                        (onComplete: Either[DeliveryException, ApnsDeliverySuccess] => Unit)
                        (implicit executionContext: ExecutionContextExecutor): Unit = {
      if (attempts.getAndIncrement() == 0) {
        onComplete(Left(FailedAPNSRequest(notificationId, token, new RuntimeException("stream closed"))))
      } else {
        onComplete(Right(ApnsDeliverySuccess(token, Instant.now())))
      }
    }
  }

  private class ClientTimeoutFailureClient extends DeliveryClient {
    type Success = ApnsDeliverySuccess
    type Payload = ApnsPayload

    val attempts = new AtomicInteger(0)
    val dryRun = false
    val payloadBuilder: Notification => Option[ApnsPayload] =
      _ => Some(ApnsPayload("{}", None, None, PushType.ALERT))

    def sendNotification(notificationId: UUID, token: String, payload: ApnsPayload, dryRun: Boolean)
                        (onComplete: Either[DeliveryException, ApnsDeliverySuccess] => Unit)
                        (implicit executionContext: ExecutionContextExecutor): Unit = {
      attempts.incrementAndGet()
      onComplete(Left(FailedAPNSRequest(notificationId, token, new RuntimeException("request timed out"), Some("ClientTimeout"))))
    }
  }

  private class FailedAPNSDeliveryClient extends DeliveryClient {
    type Success = ApnsDeliverySuccess
    type Payload = ApnsPayload

    val attempts = new AtomicInteger(0)
    val dryRun = false
    val payloadBuilder: Notification => Option[ApnsPayload] =
      _ => Some(ApnsPayload("{}", None, None, PushType.ALERT))

    def sendNotification(notificationId: UUID, token: String, payload: ApnsPayload, dryRun: Boolean)
                        (onComplete: Either[DeliveryException, ApnsDeliverySuccess] => Unit)
                        (implicit executionContext: ExecutionContextExecutor): Unit = {
      if (attempts.getAndIncrement() == 0) {
        onComplete(Left(FailedAPNSDelivery(notificationId, token, "TooManyRequests")))
      } else {
        onComplete(Right(ApnsDeliverySuccess(token, Instant.now())))
      }
    }
  }


}
