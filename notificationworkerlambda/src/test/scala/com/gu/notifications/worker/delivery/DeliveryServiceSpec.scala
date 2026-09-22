package com.gu.notifications.worker.delivery

import cats.effect.{ContextShift, IO, Timer}
import com.gu.notifications.worker.delivery.DeliveryException.FailedRequest
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
    "retry a transient failed request" in {
      implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global
      implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
      implicit val timer: Timer[IO] = IO.timer(executionContext)

      val client = new TransientFailureClient
      val service = new DeliveryServiceImpl[IO, TransientFailureClient](client)

      val result = service.send(notification, "token").compile.toList.unsafeRunSync()

      client.attempts.get shouldEqual 2
      result.count(_.isRight) shouldEqual 1
    }

    "not retry a failed request caused by a client timeout" in {
      implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global
      implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
      implicit val timer: Timer[IO] = IO.timer(executionContext)

      val client = new ClientTimeoutFailureClient
      val service = new DeliveryServiceImpl[IO, ClientTimeoutFailureClient](client)

      val result = service.send(notification, "token").compile.toList.unsafeRunSync()

      client.attempts.get shouldEqual 1
      result must contain(beLeft[DeliveryException].like {
        case failure: FailedRequest => failure.errorCode shouldEqual Some("ClientTimeout")
      })
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
        onComplete(Left(FailedRequest(notificationId, token, new RuntimeException("stream closed"))))
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
      onComplete(Left(FailedRequest(notificationId, token, new RuntimeException("request timed out"), Some("ClientTimeout"))))
    }
  }
}