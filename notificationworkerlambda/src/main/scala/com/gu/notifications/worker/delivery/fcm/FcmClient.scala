package com.gu.notifications.worker.delivery.fcm

import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.Executor

import com.google.api.core.ApiFuture
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging.{FirebaseMessaging, FirebaseMessagingException, Message}
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.gu.notifications.worker.delivery.DeliveryException.{FailedRequest, InvalidToken}
import com.gu.notifications.worker.delivery.fcm.models.payload.FcmPayload
import com.gu.notifications.worker.delivery.{DeliveryClient, FcmDeliverySuccess, FcmPayload}
import models.FcmConfig
import _root_.models.{Android, Notification, Platform}
import com.gu.notifications.worker.utils.UnwrappingExecutionException

import scala.concurrent.{ExecutionContextExecutor, Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class FcmClient private (firebaseMessaging: FirebaseMessaging, firebaseApp: FirebaseApp, config: FcmConfig)
  extends DeliveryClient {

  type Success = FcmDeliverySuccess
  type Payload = FcmPayload

  val platform: Platform = Android

  private val invalidTokenErrorCodes = Seq(
    "invalid-registration-token",
    "registration-token-not-registered"
  )

  def close(): Unit = firebaseApp.delete()

  def payloadBuilder: Notification => Option[FcmPayload] = n => FcmPayload(n, config.debug)

  def sendNotification(notificationId: UUID, token: String, payload: Payload, platform: Platform)
    (onComplete: Either[Throwable, Success] => Unit)
    (implicit executionContext: ExecutionContextExecutor): Unit = {

    val message = Message
      .builder
      .setToken(token)
      .setAndroidConfig(payload.androidConfig)
      .build


    if(config.dryRun) { // Firebase has a dry run mode but in order to get the same behavior for both APNS and Firebase we don't send the request
      onComplete(Right(FcmDeliverySuccess(token, "dryrun", dryRun = true)))
    } else {
      import FirebaseHelpers._
      firebaseMessaging
        .sendAsync(message)
        .asScala
        .onComplete {
          case Success(messageId) =>
            onComplete(Right(FcmDeliverySuccess(token, messageId)))
          case Failure(UnwrappingExecutionException(e: FirebaseMessagingException)) if invalidTokenErrorCodes.contains(e.getErrorCode) =>
            onComplete(Left(InvalidToken(notificationId, token, e.getMessage)))
          case Failure(UnwrappingExecutionException(e: FirebaseMessagingException)) =>
            onComplete(Left(FailedRequest(notificationId, token, e, Option(e.getErrorCode))))
          case Failure(NonFatal(t)) =>
            onComplete(Left(FailedRequest(notificationId, token, t)))
        }
    }
  }

}

object FcmClient {

  def apply(config: FcmConfig): Try[FcmClient] = {
    Try {
      val firebaseOptions: FirebaseOptions = new FirebaseOptions.Builder()
          .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(config.serviceAccountKey.getBytes)))
          .setConnectTimeout(10000) // 10 seconds
          .build
      FirebaseApp.initializeApp(firebaseOptions)
    }
      .map(app => new FcmClient(FirebaseMessaging.getInstance(app), app, config))
  }

}

object FirebaseHelpers {

  implicit class RichApiFuture[T](val f: ApiFuture[T]) extends AnyVal {
    def asScala(implicit e: Executor): Future[T] = {
      val p = Promise[T]()
      f.addListener(() => p.complete(Try(f.get())), e)
      p.future
    }
  }

}
