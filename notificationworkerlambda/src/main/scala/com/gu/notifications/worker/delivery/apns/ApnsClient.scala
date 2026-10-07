package com.gu.notifications.worker.delivery.apns

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.util.UUID
import java.util.concurrent.{CompletableFuture, CompletionException, TimeUnit, TimeoutException}
import com.gu.notifications.worker.delivery._
import com.gu.notifications.worker.delivery.DeliveryException.{FailedAPNSDelivery, FailedAPNSRequest, InvalidToken}
import models.ApnsConfig
import _root_.models.Notification
import com.gu.notifications.worker.delivery.apns.models.payload.ApnsPayloadBuilder
import com.gu.notifications.worker.utils.Logging
import com.eatthepath.pushy.apns.auth.ApnsSigningKey
import com.eatthepath.pushy.apns.util.{SimpleApnsPushNotification, TokenUtil}
import com.eatthepath.pushy.apns.{ApnsClientBuilder, PushNotificationResponse, ApnsClient => PushyApnsClient}
import org.slf4j.{Logger, LoggerFactory}

import java.time.{Duration, Instant}
import scala.concurrent.ExecutionContextExecutor
import scala.jdk.OptionConverters._
import scala.util.Try

class ApnsClient(private val underlying: PushyApnsClient, val config: ApnsConfig) extends DeliveryClient with Logging {

  type Success = ApnsDeliverySuccess
  type Payload = ApnsPayload
  val dryRun = config.dryRun
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private val apnsPayloadBuilder = new ApnsPayloadBuilder(config)

  private val invalidTokenErrorCodes = Set(
    "BadDeviceToken",
    "Unregistered",
    "DeviceTokenNotForTopic"
  )

  def payloadBuilder: Notification => Option[ApnsPayload] = apnsPayloadBuilder.apply _

  def sendNotification(notificationId: UUID, token: String, payload: Payload, dryRun: Boolean)
                      (implicit ece: ExecutionContextExecutor): CompletableFuture[Either[DeliveryException, Success]] = {

    if (dryRun) {
      // do not send to APNS on a dry run
      CompletableFuture.completedFuture[Either[DeliveryException, Success]](
        Right(ApnsDeliverySuccess(token, Instant.now(), dryRun = true))
      )
    } else {

      val pushNotification = new SimpleApnsPushNotification(
        TokenUtil.sanitizeTokenString(token),
        config.bundleId,
        payload.jsonString,
        //Default to no invalidation time but an hour for breaking news and 10 mins for football
        //See https://stackoverflow.com/questions/12317037/apns-notifications-ttl
        payload.ttl.map(invalidationTime).orNull,
        payload.deliveryPriority,
        payload.collapseId.orNull
      )

      val start = Instant.now

      underlying
        .sendNotification(pushNotification)
        // Exceptionally completes this CompletableFuture with a TimeoutException if not otherwise completed before the given timeout.
        .orTimeout(20, TimeUnit.SECONDS)

        // The exception passed to .handle depends on how the stage completed exceptionally:
        // Wrapped in CompletionException VS raw exception e.g. TimoutException/TransportException/SLLException
        .handle[Either[DeliveryException, Success]] {
          (response: PushNotificationResponse[SimpleApnsPushNotification], error: Throwable) =>

            val latency = Duration.between(start, Instant.now).toMillis

            // successful APNS request
            if (error == null) {
              logger.info(Map(
                "worker.individualRequestLatency" -> latency,
                "notificationId" -> notificationId,
                "token" -> token,
              ), "Individual send request completed")

              if (response.isAccepted) {
                Right(ApnsDeliverySuccess(token, Instant.now()))
              } else {
                // APNS rejected the notification after delivery to the APNS server.
                val invalidationTimestamp = response.getTokenInvalidationTimestamp.toScala
                  .map(instant => Timestamp.from(instant).toLocalDateTime)

                val ApnsDeliveryError = if (invalidationTimestamp.isDefined || invalidTokenErrorCodes.contains(response.getRejectionReason)) {
                  InvalidToken(notificationId, token, response.getRejectionReason, invalidationTimestamp)
                } else {
                  FailedAPNSDelivery(notificationId, token, response.getRejectionReason)
                }
                Left(ApnsDeliveryError)
              }

              // unsuccessful APNS request
            } else {
              // normalise exception so downstream match sees underlying failure not a wrapper
              val reqError = error match {
                // wrapped Exceptions
                case ce: CompletionException if ce.getCause != null => ce.getCause // cause is a throwable.
                // unwrapped Exceptions eg. TimoutException, TransportException, SSLException
                case other => other
              }
              reqError match {
                case timeout: TimeoutException =>
                  logger.info(Map(
                    "worker.individualRequestLatency" -> latency,
                    "notificationId" -> notificationId,
                    "token" -> token,
                  ), s"Individual send request timed out. Cause: ${timeout.getCause}, message: ${timeout.getMessage}")
                  Left(FailedAPNSRequest(notificationId, token, new TimeoutException("No APNs response received in time"), Some("ClientTimeout")))
                case other =>
                  logger.error(Map(
                    "worker.individualRequestLatency" -> latency,
                    "notificationId" -> notificationId,
                    "token" -> token,
                  ), s"Failed APNS Request. Cause: $other")
                  Left(FailedAPNSRequest(notificationId, token, other))
              }
            }
        }
    }
  }

  private def invalidationTime(timeToLive: Long): Instant = Instant.now().plus(Duration.ofMillis(timeToLive))
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
        .setConnectionTimeout(Duration.ofSeconds(10))
        .setConcurrentConnections(config.concurrentPushyConnections)
        .build()
    ).map(pushyClient => new ApnsClient(pushyClient, config))
  }

}

