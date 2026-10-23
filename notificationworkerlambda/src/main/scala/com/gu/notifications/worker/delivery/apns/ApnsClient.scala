package com.gu.notifications.worker.delivery.apns

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.util.{Timer, UUID}
import java.util.concurrent.{TimeUnit, TimeoutException}
import com.gu.notifications.worker.delivery._
import com.gu.notifications.worker.delivery.DeliveryException.{FailedAPNSDelivery, FailedRequest, InvalidToken}
import models.ApnsConfig
import _root_.models.Notification
import com.gu.notifications.worker.delivery.apns.models.payload.ApnsPayloadBuilder
import com.gu.notifications.worker.utils.Logging
import com.turo.pushy.apns.auth.ApnsSigningKey
import com.turo.pushy.apns.util.concurrent.PushNotificationFuture
import com.turo.pushy.apns.util.{SimpleApnsPushNotification, TokenUtil}
import com.turo.pushy.apns.{ApnsClientBuilder, PushNotificationResponse, ApnsClient => PushyApnsClient}
import org.joda.time.DateTime
import org.slf4j.{Logger, LoggerFactory}

import java.time.{Duration, Instant}
import scala.concurrent.ExecutionContextExecutor
import scala.util.Try

class ApnsClient(private val underlying: PushyApnsClient, val config: ApnsConfig) extends DeliveryClient with Logging {

  type Success = ApnsDeliverySuccess
  type Payload = ApnsPayload
  val dryRun = config.dryRun
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)
  private val timer = new Timer("ios-worker-timeout", true)

  private val apnsPayloadBuilder = new ApnsPayloadBuilder(config)

  private val invalidTokenErrorCodes = Set(
    "BadDeviceToken",
    "Unregistered",
    "DeviceTokenNotForTopic"
  )

  def payloadBuilder: Notification => Option[ApnsPayload] = apnsPayloadBuilder.apply _

  def sendNotification(notificationId: UUID, token: String, payload: Payload, dryRun: Boolean)
    (onComplete: Either[DeliveryException, Success] => Unit)
    (implicit ece: ExecutionContextExecutor): Unit = {

    val pushNotification = new SimpleApnsPushNotification(
      TokenUtil.sanitizeTokenString(token),
      config.bundleId,
      payload.jsonString,
      //Default to no invalidation time but an hour for breaking news and 10 mins for football
      //See https://stackoverflow.com/questions/12317037/apns-notifications-ttl
      payload.ttl.map(invalidationTime(_).toDate).orNull,
      payload.deliveryPriority,
      payload.collapseId.orNull
    )

    type Feedback = PushNotificationFuture[SimpleApnsPushNotification, PushNotificationResponse[SimpleApnsPushNotification]]

    def responseHandler(start: Instant) = new PushNotificationResponseListenerWithTimeout[SimpleApnsPushNotification]() {

      private def fields(outcome: String): Map[String, Any] = Map(
        "worker.individualRequestLatency" -> Duration.between(start, Instant.now).toMillis,
        "notificationId" -> notificationId,
        "apns.outcome" -> outcome,
      )

      private def responseFields(response: PushNotificationResponse[SimpleApnsPushNotification]): Map[String, Any] =
        Map("apns.id" -> String.valueOf(response.getApnsId)) ++
          Option(response.getRejectionReason).map("apns.rejectionReason" -> _)

      def timeout(): Unit = {
        logger.info(fields("timeout"), "Individual send request timed out")
        onComplete(Left(FailedRequest(notificationId, token, new TimeoutException("No APNs response received in time"), Some("ClientTimeout"))))
      }

      override def operationCompleteWithoutTimeout(feedback: Feedback): Unit = {
        if (feedback.isSuccess) {
          val response = feedback.getNow
          if (response.isAccepted) {
            logger.info(fields("accepted") ++ responseFields(response), "Individual send request completed")
            onComplete(Right(ApnsDeliverySuccess(token, Instant.now())))
          } else {
            val invalidationTimestamp = Option(response.getTokenInvalidationTimestamp)
              .map(d => new Timestamp(d.getTime).toLocalDateTime)
            val isInvalidToken = invalidationTimestamp.isDefined || invalidTokenErrorCodes.contains(response.getRejectionReason)

            logger.info(fields(if (isInvalidToken) "invalidToken" else "rejected") ++ responseFields(response), "Individual send request completed")

            val error = if (isInvalidToken) {
              InvalidToken(notificationId, token, response.getRejectionReason, invalidationTimestamp)
            } else {
              FailedAPNSDelivery(notificationId, token, response.getRejectionReason)
            }
            onComplete(Left(error))
          }
        } else {
          val cause = feedback.cause()
          logger.info(fields("requestFailed") ++ Map(
            "apns.errorType" -> Option(cause).map(_.getClass.getSimpleName).getOrElse("unknown"),
            "apns.errorMessage" -> Option(cause).map(_.getMessage).getOrElse(""),
            "apns.cancelled" -> feedback.isCancelled,
          ), "Individual send request completed")
          onComplete(Left(FailedRequest(notificationId, token, cause)))
        }
      }
    }

    if(dryRun) {
      onComplete(Right(ApnsDeliverySuccess(token, Instant.now(), dryRun = true)))
    } else {
      val start = Instant.now
      val futureResult = underlying.sendNotification(pushNotification)
      val handler = responseHandler(start)
      handler.startTimeout(timer, timeoutInMs = 20000L)
      futureResult.addListener(handler)
    }
  }

  private def invalidationTime(timeToLive: Long) : DateTime = DateTime.now().plus(timeToLive)
}

object ApnsClient {

  def apply(config: ApnsConfig): Try[ApnsClient] = {
    val apnsServer =
      if (config.sendingToProdServer) ApnsClientBuilder.PRODUCTION_APNS_HOST
      else ApnsClientBuilder.DEVELOPMENT_APNS_HOST

    val signingKey = ApnsSigningKey.loadFromInputStream(
      new ByteArrayInputStream(config.certificate.getBytes(StandardCharsets.UTF_8)),
      config.teamId,
      config.keyId
    )

    Try(
      new ApnsClientBuilder()
        .setApnsServer(apnsServer)
        .setSigningKey(signingKey)
        .setConnectionTimeout(10, TimeUnit.SECONDS)
        .setConcurrentConnections(config.concurrentPushyConnections)
        .build()
    ).map(pushyClient => new ApnsClient(pushyClient, config))
  }

}

