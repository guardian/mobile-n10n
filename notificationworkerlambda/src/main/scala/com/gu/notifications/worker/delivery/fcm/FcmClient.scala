package com.gu.notifications.worker.delivery.fcm

import _root_.models.Notification
import com.google.api.core.ApiFuture
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging._
import com.google.firebase.{ErrorCode, FirebaseApp, FirebaseOptions}
import com.gu.notifications.worker.delivery.DeliveryException.{BatchCallFailedRequest, FailedRequest, InvalidToken, UnknownReasonFailedRequest}
import com.gu.notifications.worker.delivery.fcm.models.FcmConfig
import com.gu.notifications.worker.delivery.fcm.models.payload.FcmPayloadBuilder
import com.gu.notifications.worker.delivery.fcm.oktransport.OkGoogleHttpTransport
import com.gu.notifications.worker.delivery.{DeliveryClient, FcmBatchDeliverySuccess, FcmDeliverySuccess, FcmPayload}
import com.gu.notifications.worker.utils.NotificationParser.logger
import com.gu.notifications.worker.utils.UnwrappingExecutionException

import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.Executor
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class FcmClient (firebaseMessaging: FirebaseMessaging, firebaseApp: FirebaseApp, config: FcmConfig)
  extends DeliveryClient {

  type Success = FcmDeliverySuccess
  type BatchSuccess = FcmBatchDeliverySuccess
  type Payload = FcmPayload
  val dryRun = config.dryRun

  private val invalidTokenErrorCodes = Set(
    MessagingErrorCode.INVALID_ARGUMENT,
    MessagingErrorCode.UNREGISTERED,
    MessagingErrorCode.SENDER_ID_MISMATCH,
    ErrorCode.PERMISSION_DENIED
  )

  def close(): Unit = firebaseApp.delete()

  def payloadBuilder: Notification => Option[FcmPayload] = n => FcmPayloadBuilder(n, config.debug)

  def isUnregistered(e: FirebaseMessagingException): Boolean = {
    (e.getErrorCode, e.getMessage) match {
      case (ErrorCode.NOT_FOUND, "Requested entity was not found.") => true
      case (_, _) => false
    }
  }

  def sendNotification(notificationId: UUID, token: String, payload: Payload, dryRun: Boolean)
    (onComplete: Either[Throwable, Success] => Unit)
    (implicit executionContext: ExecutionContextExecutor): Unit = {

    val message = Message
      .builder
      .setToken(token)
      .setAndroidConfig(payload.androidConfig)
      .build


    if(dryRun) { // Firebase has a dry run mode but in order to get the same behavior for both APNS and Firebase we don't send the request
      onComplete(Right(FcmDeliverySuccess(token, "dryrun", dryRun = true)))
    } else {
      import FirebaseHelpers._
      firebaseMessaging
        .sendAsync(message)
        .asScala
        .onComplete {
          case Success(messageId) =>
            onComplete(Right(FcmDeliverySuccess(token, messageId)))
          case Failure(UnwrappingExecutionException(e: FirebaseMessagingException)) if invalidTokenErrorCodes.contains(e.getErrorCode) || isUnregistered(e) =>
            onComplete(Left(InvalidToken(notificationId, token, e.getMessage)))
          case Failure(UnwrappingExecutionException(e: FirebaseMessagingException)) =>
            onComplete(Left(FailedRequest(notificationId, token, e, Option(e.getErrorCode.toString))))
          case Failure(NonFatal(t)) =>
            onComplete(Left(FailedRequest(notificationId, token, t)))
          case Failure(_) =>
            onComplete(Left(UnknownReasonFailedRequest(notificationId, token)))
        }
    }
  }
  def sendBatchNotification(notificationId: UUID, token: List[String], payload: Payload, dryRun: Boolean)
    (onComplete: Either[Throwable, Success] => Unit) // Future[Either[Throwable, Success]]
    (implicit executionContext: ExecutionContextExecutor): Unit = {

    val message = MulticastMessage
      .builder
      .addAllTokens(token.asJava)
      .setAndroidConfig(payload.androidConfig)
      .build


    if (dryRun) { // Firebase has a dry run mode but in order to get the same behavior for both APNS and Firebase we don't send the request
      onComplete(Right(FcmDeliverySuccess(s"token batch succeeded: ${token.head}", "dryrun", dryRun = true)))
    } else {
      import FirebaseHelpers._
      firebaseMessaging
        .sendMulticastAsync(message)
        .asScala
        .onComplete {
          case Success(batchResponse) =>
            batchResponse.getSuccessCount match {
              case count if count == token.size =>
                onComplete(Right(FcmDeliverySuccess(s"Entire batch succeeded", notificationId.toString)))
              case _ =>
                batchResponse.getResponses.asScala.toList.foreach { r => {
                  if (!r.isSuccessful) {
                    logger.info(s"Token in batch response failed: ${r.getException.getMessagingErrorCode} because: ${r.getException.getMessage}")
                    // https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/messaging/SendResponse
                    // how to correlate failed responses to a tokenid?
                    // if the message fails then messageId is null, and I don't _think_ messageId == token
                    // docs say that the order of the response maps exactly to the order of the request
                    // so we need can get the token id of a failure from the position in the response compared to the position in the request
                    // https://firebase.google.com/docs/cloud-messaging/send-message#java_1
                    r.getException match {
                      case UnwrappingExecutionException(e: FirebaseMessagingException) if invalidTokenErrorCodes.contains(e.getMessagingErrorCode) =>
                        onComplete(Left(InvalidToken(notificationId, r.getMessageId, e.getMessage)))
                      case UnwrappingExecutionException(e: FirebaseMessagingException) =>
                        onComplete(Left(FailedRequest(notificationId, r.getMessageId, e, Option("failure"))))
                      case _ =>
                        onComplete(Left(UnknownReasonFailedRequest(notificationId, r.getMessageId)))
                    }
                  } else {
                    onComplete(Right(FcmDeliverySuccess(s"Token in batch response succeeded", r.getMessageId)))
                  }
                }
              }
            }
          case Failure(x) =>
            onComplete(Left(BatchCallFailedRequest(notificationId, s"Multicast Async Response Failure: ${x.getCause}")))
        }
    }
  }

}

object FcmClient {
  def apply(config: FcmConfig): Try[FcmClient] = {
    Try {
      val firebaseOptions: FirebaseOptions = FirebaseOptions.builder()
          .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(config.serviceAccountKey.getBytes)))
          .setHttpTransport(new OkGoogleHttpTransport)
          .setConnectTimeout(10000) // 10 seconds
          .build
      FirebaseApp.initializeApp(firebaseOptions)
    }.map(app => new FcmClient(FirebaseMessaging.getInstance(app), app, config))
  }

}

object FirebaseHelpers {

  implicit class RichApiFuture[T](val f: ApiFuture[T]) extends AnyVal {
    def asScala(implicit e: ExecutionContext): Future[T] =
      Future {
        f.get()
      }
  }

}
