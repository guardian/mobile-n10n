package apnsworker

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import _root_.models.{Notification}
import java.sql.Timestamp

import apnsworker.models.{ApnsConfig, ApnsFeedbackFailure}
import com.turo.pushy.apns.auth.ApnsSigningKey
import com.turo.pushy.apns.util.concurrent.{PushNotificationFuture, PushNotificationResponseListener}
import com.turo.pushy.apns.util.{ApnsPayloadBuilder, SimpleApnsPushNotification, TokenUtil}
import com.turo.pushy.apns.{ApnsClientBuilder, PushNotificationResponse, ApnsClient => PushyApnsClient}

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

  def sendNotification(token: String, notification: Notification)
    (onComplete: Either[Throwable, String] => Unit)
    (client: PushyApnsClient, config: ApnsConfig)
    (implicit executionContext: ExecutionContext): Unit = {

    val payloadBuilder = new ApnsPayloadBuilder()
    payloadBuilder.setAlertTitle(notification.title)
    payloadBuilder.setAlertBody(notification.message)

    //      payloadBuilder.setActionButtonLabel()
    //      payloadBuilder.setAlertSubtitle()
    //      payloadBuilder.setBadgeNumber()
    //      payloadBuilder.setCategoryName()
    //      payloadBuilder.setContentAvailable()
    //      payloadBuilder.setLaunchImageFileName()
    //      payloadBuilder.setLocalizedActionButtonKey()
    //      payloadBuilder.setLocalizedAlertMessage()
    //      payloadBuilder.setLocalizedAlertSubtitle()
    //      payloadBuilder.setLocalizedAlertTitle()
    //      payloadBuilder.setMutableContent()
    //      payloadBuilder.setPreferStringRepresentationForAlerts()
    //      payloadBuilder.setShowActionButton()
    //      payloadBuilder.setSound()
    //      payloadBuilder.setThreadId()
    //      payloadBuilder.setUrlArguments()
    //      payloadBuilder.addCustomProperty()

    val payload = payloadBuilder.buildWithDefaultMaximumLength()
    val pushNotification = new SimpleApnsPushNotification(
      TokenUtil.sanitizeTokenString(token),
      config.bundleId,
      payload
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
          val msg = s"Error: APNS request failed. Cause: ${responseF.cause()}. Notification: ${notification.id}, device :${token}. "
          onComplete(Left(new RuntimeException()))
        }
      }
    }

    client
      .sendNotification(pushNotification)
      .addListener(responseHandler)
  }

}

