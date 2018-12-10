package com.gu.notifications.worker.delivery.apns

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.util.UUID
import java.util.concurrent.TimeUnit

import com.gu.notifications.worker.delivery._
import com.gu.notifications.worker.delivery.DeliveryException.{FailedDelivery, FailedRequest, InvalidToken}
import models.ApnsConfig
import _root_.models.{Notification, Platform, iOS, Newsstand}
import com.gu.notifications.worker.delivery.apns.models.payload.ApnsPayload
import com.turo.pushy.apns.auth.ApnsSigningKey
import com.turo.pushy.apns.util.concurrent.{PushNotificationFuture, PushNotificationResponseListener}
import com.turo.pushy.apns.util.{SimpleApnsPushNotification, TokenUtil}
import com.turo.pushy.apns.{ApnsClientBuilder, DeliveryPriority, PushNotificationResponse, ApnsClient => PushyApnsClient}

import scala.concurrent.ExecutionContextExecutor
import scala.util.Try

class ApnsClient(private val underlying: PushyApnsClient, val config: ApnsConfig) extends DeliveryClient {

  type Success = ApnsDeliverySuccess
  type Payload = ApnsPayload

  val platform: Platform = iOS

  private val invalidTokenErrorCodes = Seq(
    "BadDeviceToken",
    "Unregistered"
  )

  def close(): Unit = underlying.close().get

  def payloadBuilder: Notification => Option[ApnsPayload] = ApnsPayload.apply _

  def sendNotification(notificationId: UUID, token: String, payload: Payload, platform: Platform)
    (onComplete: Either[Throwable, Success] => Unit)
    (implicit ece: ExecutionContextExecutor): Unit = {

    val bundleId = platform match {
      case Newsstand => config.newsstandBundleId
      case _ => config.bundleId
    }

    val collapseId = notificationId.toString
    val pushNotification = new SimpleApnsPushNotification(
      TokenUtil.sanitizeTokenString(token),
      bundleId,
      payload.jsonString,
      null, // No invalidation time
      DeliveryPriority.IMMEDIATE,
      collapseId
    )

    type Feedback = PushNotificationFuture[SimpleApnsPushNotification, PushNotificationResponse[SimpleApnsPushNotification]]

    def responseHandler = new PushNotificationResponseListener[SimpleApnsPushNotification]() {
      override def operationComplete(feedback: Feedback) {
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
          onComplete(Left(FailedRequest(notificationId, token, feedback.cause())))
        }
      }
    }

    if(config.dryRun) {
      onComplete(Right(ApnsDeliverySuccess(token, dryRun = true)))
    } else {
      underlying
        .sendNotification(pushNotification)
        .addListener(responseHandler)
    }
  }
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

