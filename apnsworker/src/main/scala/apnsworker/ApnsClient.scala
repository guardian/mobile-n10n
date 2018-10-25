package apnsworker

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import java.sql.Timestamp
import java.util.UUID

import apnsworker.models.{ApnsConfig, ApnsFeedbackFailure}
import apnsworker.payload.ApnsPayload
import com.turo.pushy.apns.auth.ApnsSigningKey
import com.turo.pushy.apns.util.concurrent.{PushNotificationFuture, PushNotificationResponseListener}
import com.turo.pushy.apns.util.{SimpleApnsPushNotification, TokenUtil}
import com.turo.pushy.apns.{ApnsClientBuilder, DeliveryPriority, PushNotificationResponse, ApnsClient => PushyApnsClient}

import scala.concurrent.ExecutionContext
import scala.util.Try

object ApnsClient {

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
    (onComplete: Either[Throwable, String] => Unit)
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
      override def operationComplete(responseF: Feedback) {
        if (responseF.isSuccess) {
          val response = responseF.getNow
          if (response.isAccepted) {
            onComplete(Right(token))
          } else {
            val feedbackFailure = ApnsFeedbackFailure(
              token,
              response.getApnsId,
              response.getRejectionReason,
              Option(response.getTokenInvalidationTimestamp).map(d => new Timestamp(d.getTime()).toLocalDateTime())
            )
            onComplete(Left(feedbackFailure))
          }
        } else {
          val errorMsg = s"Error: APNS request failed (Notification $notificationId, Token: $token). Cause: ${responseF.cause()}"
          onComplete(Left(new RuntimeException(errorMsg)))
        }
      }
    }

    if(config.dryRun) {
      val msg = s"Dry RUN !!!! Notification has not be sent (Notification $notificationId, Token: $token)}"
      onComplete(Left(new RuntimeException(msg)))
    } else {
      client
        .sendNotification(pushNotification)
        .addListener(responseHandler)
    }
  }

}

