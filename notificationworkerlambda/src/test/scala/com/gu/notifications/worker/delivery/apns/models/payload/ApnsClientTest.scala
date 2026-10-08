package com.gu.notifications.worker.delivery.apns

import com.eatthepath.pushy.apns.{PushNotificationResponse, PushType, ApnsClient => PushyApnsClient}
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture
import com.gu.notifications.worker.delivery.{ApnsDeliverySuccess, ApnsPayload, DeliveryException}
import com.gu.notifications.worker.delivery.DeliveryException.{FailedAPNSDelivery, FailedAPNSRequest, InvalidToken}
import com.gu.notifications.worker.delivery.apns.models.ApnsConfig
import org.mockito.Mockito.{doReturn, when}
import org.mockito.ArgumentMatchers.any
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import java.io.IOException
import java.time.Instant
import java.util.{Optional, UUID}
import java.util.concurrent.{CompletionException, TimeoutException}
import scala.concurrent.ExecutionContext.Implicits.global

//todo check all cases

class ApnsClientTest extends Specification with Mockito {

  "ApnsClient.sendNotification" should {

    "short-circuit on dryRun without calling the underlying pushy client" in new ApnsScope {
      val result = send(dryRun = true)

      result must beRight[ApnsDeliverySuccess].like { case s =>
        (s.token mustEqual token) and (s.dryRun must beTrue)
      }
      there was no(mockUnderlying).sendNotification(any[SimpleApnsPushNotification])
    }

    "return ApnsDeliverySuccess when the notification is accepted" in new ApnsScope {
      when(response.isAccepted).thenReturn(true)
      stubSend(completedWith(response))

      send() must beRight[ApnsDeliverySuccess]
    }

    "map a rejection with an invalid-token reason to InvalidToken" in new ApnsScope {
      when(response.isAccepted).thenReturn(false)
      when(response.getRejectionReason).thenReturn(Optional.of("BadDeviceToken"))
      when(response.getTokenInvalidationTimestamp).thenReturn(Optional.empty[Instant]())
      stubSend(completedWith(response))

      send() must beLeft[DeliveryException].like { case InvalidToken(_, _, reason, _) =>
        reason mustEqual "BadDeviceToken"
      }
    }

    "map a rejection carrying an invalidation timestamp to InvalidToken (even if the reason is not in the set)" in new ApnsScope {
      when(response.isAccepted).thenReturn(false)
      when(response.getRejectionReason).thenReturn(Optional.of("PayloadTooLarge"))
      when(response.getTokenInvalidationTimestamp).thenReturn(Optional.of(Instant.now()))
      stubSend(completedWith(response))

      send() must beLeft[DeliveryException].like { case _: InvalidToken => ok }
    }

    "map a generic rejection (no timestamp, reason not in set) to FailedAPNSDelivery" in new ApnsScope {
      when(response.isAccepted).thenReturn(false)
      when(response.getRejectionReason).thenReturn(Optional.of("PayloadTooLarge"))
      when(response.getTokenInvalidationTimestamp).thenReturn(Optional.empty[Instant]())
      stubSend(completedWith(response))

      send() must beLeft[DeliveryException].like { case FailedAPNSDelivery(_, _, reason) =>
        reason mustEqual "PayloadTooLarge"
      }
    }

    "map a raw TimeoutException to a ClientTimeout FailedAPNSRequest" in new ApnsScope {
      stubSend(failedWith(new TimeoutException("boom")))

      send() must beLeft[DeliveryException].like { case FailedAPNSRequest(_, _, _, errorCode) =>
        errorCode must beSome("ClientTimeout")
      }
    }

    "unwrap a Pushy CompletionException and preserve the underlying cause as a FailedAPNSRequest" in new ApnsScope {
      val cause = new IOException("Broken pipe")
      stubSend(failedWith(new CompletionException(cause)))

      send() must beLeft[DeliveryException].like { case FailedAPNSRequest(_, _, c, errorCode) =>
        (c mustEqual cause) and (errorCode must beNone)
      }
    }

    "pass through a raw (unwrapped) transport exception as a FailedAPNSRequest" in new ApnsScope {
      val cause = new IOException("Connection reset by peer")
      stubSend(failedWith(cause))

      send() must beLeft[DeliveryException].like { case FailedAPNSRequest(_, _, c, errorCode) =>
        (c mustEqual cause) and (errorCode must beNone)
      }
    }
  }
}

trait ApnsScope extends Scope {

  implicit val ec: scala.concurrent.ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  val notificationId: UUID = UUID.fromString("0000-0000-0000-0000-000000000001")
  val token = "test-token"
  val payload: ApnsPayload = ApnsPayload("""{"aps":{"alert":"hi"}}""", None, None, PushType.ALERT)

  val config: ApnsConfig = ApnsConfig(
    teamId = "teamId",
    bundleId = "bundleId",
    keyId = "keyId",
    certificate = "certificate",
    mapiBaseUrl = "https://mapi.example.com",
    sendingToProdServer = false,
    dryRun = false,
    concurrentPushyConnections = 1,
    maxConcurrency = 1,
  )

  val mockUnderlying: PushyApnsClient = Mockito.mock[PushyApnsClient]
  val response: PushNotificationResponse[SimpleApnsPushNotification] =
    Mockito.mock[PushNotificationResponse[SimpleApnsPushNotification]]

  val client = new ApnsClient(mockUnderlying, config)

  private val samplePush = new SimpleApnsPushNotification(token, config.bundleId, payload.jsonString)

  private def newFuture =
    new PushNotificationFuture[SimpleApnsPushNotification, PushNotificationResponse[SimpleApnsPushNotification]](
      samplePush,
    )

  def completedWith(r: PushNotificationResponse[SimpleApnsPushNotification]) = {
    val f = newFuture; f.complete(r); f
  }
  def failedWith(ex: Throwable) = {
    val f = newFuture; f.completeExceptionally(ex); f
  }
  def stubSend(
      f: PushNotificationFuture[SimpleApnsPushNotification, PushNotificationResponse[SimpleApnsPushNotification]],
  ): Unit =
    doReturn(f).when(mockUnderlying).sendNotification(any[SimpleApnsPushNotification]())

  // handle(...) runs inline because the stubbed future is already complete, so .get() is deterministic
  def send(
      dryRun: Boolean = false,
  ): Either[com.gu.notifications.worker.delivery.DeliveryException, ApnsDeliverySuccess] =
    client.sendNotification(notificationId, token, payload, dryRun).get()
}
