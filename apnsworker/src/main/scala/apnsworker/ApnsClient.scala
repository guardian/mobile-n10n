package apnsworker

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.util.UUID

import apnsworker.models.ApnsException.{ApnsDryRun, ApnsFailedDelivery, ApnsFailedRequest, ApnsInvalidToken}
import apnsworker.models.{ApnsConfig, ApnsException}
import apnsworker.payload.ApnsPayload
import com.turo.pushy.apns.auth.ApnsSigningKey
import com.turo.pushy.apns.util.concurrent.{PushNotificationFuture, PushNotificationResponseListener}
import com.turo.pushy.apns.util.{SimpleApnsPushNotification, TokenUtil}
import com.turo.pushy.apns.{ApnsClientBuilder, DeliveryPriority, PushNotificationResponse, ApnsClient => PushyApnsClient}

import scala.concurrent.ExecutionContext
import scala.util.Try

object ApnsClient {

  type Token = String
  type ApnsResponse = Either[ApnsException, Token]

  def apply(config: ApnsConfig): Try[PushyApnsClient] = {
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
        .build()
    )
  }

  def sendNotification(notificationId: UUID, token: String, payload: ApnsPayload)
    (onComplete: ApnsResponse => Unit)
    (client: PushyApnsClient, config: ApnsConfig)
    (implicit executionContext: ExecutionContext): Unit = {

    val collapseId = notificationId.toString
    val pushNotification = new SimpleApnsPushNotification(
      TokenUtil.sanitizeTokenString(token),
      config.bundleId,
      payload.value,
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
            onComplete(Right(token))
          } else {
            val error =
              Option(response.getTokenInvalidationTimestamp)
                .map { d =>
                  ApnsInvalidToken(
                    notificationId,
                    token,
                    response.getRejectionReason,
                    new Timestamp(d.getTime()).toLocalDateTime()
                  )
                }
                .getOrElse(ApnsFailedDelivery(notificationId, token, response.getRejectionReason))
            onComplete(Left(error))
          }
        } else {
          onComplete(Left(ApnsFailedRequest(notificationId, token, feedback.cause())))
        }
      }
    }

    if(config.dryRun) {
      onComplete(Left(ApnsDryRun(notificationId, token)))
    } else {
      client
        .sendNotification(pushNotification)
        .addListener(responseHandler)
    }
  }

}

