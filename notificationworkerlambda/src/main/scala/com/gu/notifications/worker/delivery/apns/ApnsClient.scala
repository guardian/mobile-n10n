package com.gu.notifications.worker.delivery.apns

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.util.{Date, UUID}
import java.util.concurrent.TimeUnit

import com.gu.notifications.worker.delivery._
import com.gu.notifications.worker.delivery.DeliveryException.{FailedDelivery, FailedRequest, InvalidToken}
import models.ApnsConfig
import _root_.models.{Ios, Newsstand, Notification, Platform}
import com.gu.notifications.worker.delivery.apns.models.payload.ApnsPayloadBuilder
import com.turo.pushy.apns.auth.ApnsSigningKey
import com.turo.pushy.apns.util.concurrent.{PushNotificationFuture, PushNotificationResponseListener}
import com.turo.pushy.apns.util.{SimpleApnsPushNotification, TokenUtil}
import com.turo.pushy.apns.{ApnsClientBuilder, DeliveryPriority, PushNotificationResponse, ApnsClient => PushyApnsClient}
import org.joda.time.DateTime
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContextExecutor
import scala.util.Try

class ApnsClient(private val underlying: PushyApnsClient, val config: ApnsConfig) extends DeliveryClient {

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

  def close(): Unit = underlying.close().get

  def payloadBuilder: Notification => Option[ApnsPayload] = apnsPayloadBuilder.apply _

  def sendNotification(notificationId: UUID, token: String, payload: Payload, dryRun: Boolean)
    (onComplete: Either[Throwable, Success] => Unit)
    (implicit ece: ExecutionContextExecutor): Unit = {

    val pushNotification = new SimpleApnsPushNotification(
      TokenUtil.sanitizeTokenString(token),
      config.bundleId,
      payload.jsonString,
      //Default to no invalidation time but an hour for breaking news and 10 mins for football
      //See https://stackoverflow.com/questions/12317037/apns-notifications-ttl
      payload.ttl.map(invalidationTime(_).toDate).orNull,
      DeliveryPriority.IMMEDIATE,
      payload.collapseId.orNull
    )

    type Feedback = PushNotificationFuture[SimpleApnsPushNotification, PushNotificationResponse[SimpleApnsPushNotification]]

    def responseHandler = new PushNotificationResponseListener[SimpleApnsPushNotification]() {
      override def operationComplete(feedback: Feedback): Unit = {
        if (feedback.isSuccess) {
          val response = feedback.getNow
          if (response.isAccepted) {
            onComplete(Right(ApnsDeliverySuccess(token)))
          } else {
            val invalidationTimestamp = Option(response.getTokenInvalidationTimestamp)
              .map(d => new Timestamp(d.getTime).toLocalDateTime)

            val error = if (invalidationTimestamp.isDefined || invalidTokenErrorCodes.contains(response.getRejectionReason)) {
              InvalidToken(notificationId, token, response.getRejectionReason, invalidationTimestamp)
            } else {
              FailedDelivery(notificationId, token, response.getRejectionReason)
            }
            onComplete(Left(error))
          }
        } else {
          val debug =
            s"""Failed Request
               |isSuccess: ${feedback.isSuccess}, isDone: ${feedback.isDone}, isCancelled: ${feedback.isCancelled}
               |getNow: ${Option(feedback.getNow)}
               |cause: ${feedback.cause()}
               |""".stripMargin
          logger.error(debug)
          onComplete(Left(FailedRequest(notificationId, token, feedback.cause())))
        }
      }
    }

    if(dryRun) {
      onComplete(Right(ApnsDeliverySuccess(token, dryRun = true)))
    } else {
      underlying
        .sendNotification(pushNotification)
        .addListener(responseHandler)
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
        .build()
    ).map(pushyClient => new ApnsClient(pushyClient, config))
  }

}

