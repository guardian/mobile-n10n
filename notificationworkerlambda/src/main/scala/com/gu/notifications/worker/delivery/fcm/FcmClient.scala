package com.gu.notifications.worker.delivery.fcm

import _root_.models.Notification
import com.google.api.core.{ApiFuture, ApiFutureCallback, ApiFutures}
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging._
import com.google.firebase.{ErrorCode, FirebaseApp, FirebaseOptions}
import com.gu.notifications.worker.delivery.DeliveryException.{BatchCallFailedRequest, FailedRequest, InvalidToken, UnknownReasonFailedRequest}
import com.gu.notifications.worker.delivery.fcm.models.FcmConfig
import com.gu.notifications.worker.delivery.fcm.models.payload.FcmPayloadBuilder
import com.gu.notifications.worker.delivery.fcm.oktransport.OkGoogleHttpTransport
import com.gu.notifications.worker.delivery.{DeliveryClient, DeliveryException, FcmBatchDeliverySuccess, FcmDeliverySuccess, FcmPayload}
import com.gu.notifications.worker.utils.Logging
import org.slf4j.LoggerFactory
import com.gu.notifications.worker.utils.UnwrappingExecutionException

import java.io.ByteArrayInputStream
import java.time.{Duration, Instant}
import java.util.UUID
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class FcmClient (firebaseMessaging: FirebaseMessaging, firebaseApp: FirebaseApp, config: FcmConfig)
  extends DeliveryClient with Logging {

  type Success = FcmDeliverySuccess
  type BatchSuccess = FcmBatchDeliverySuccess
  type Payload = FcmPayload
  val dryRun = config.dryRun

  implicit val logger = LoggerFactory.getLogger(this.getClass)

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
    (onAPICallComplete: Either[DeliveryException, Success] => Unit)
    (implicit executionContext: ExecutionContextExecutor) = {

    val message = Message
      .builder
      .setToken(token)
      .setAndroidConfig(payload.androidConfig)
      .build

    if (dryRun) { // Firebase has a dry run mode but in order to get the same behavior for both APNS and Firebase we don't send the request
      onAPICallComplete(Right(FcmDeliverySuccess(token, "dryrun", dryRun = true)))
    } else {
      import FirebaseHelpers._
      val start = Instant.now
      firebaseMessaging
        .sendAsync(message)
        .asScala
        .onComplete { response => {
            logger.info(Map(
              "worker.individualRequestLatency" -> Duration.between(start, Instant.now).toMillis,
              "notificationId" -> notificationId,
            ), "Individual send request completed")
            onAPICallComplete(parseSendResponse(notificationId, token, response))
          }
        }
    }
  }

  def sendBatchNotification(notificationId: UUID, tokens: List[String], payload: Payload, dryRun: Boolean)
    (onAPICallComplete: Either[DeliveryException, BatchSuccess] => Unit)
    (implicit executionContext: ExecutionContextExecutor) = {

    val message = MulticastMessage
      .builder
      .addAllTokens(tokens.asJava)
      .setAndroidConfig(payload.androidConfig)
      .build

    if (dryRun) { // Firebase has a dry run mode but in order to get the same behavior for both APNS and Firebase we don't send the request
      onAPICallComplete(Right(
        FcmBatchDeliverySuccess(
          List.fill(tokens.size)(Right(FcmDeliverySuccess(s"success", "dryrun", dryRun = true))),
          notificationId.toString,
        )))
    } else {
      import FirebaseHelpers._
      val start = Instant.now
      firebaseMessaging
        .sendMulticastAsync(message)
        .asScala
        .onComplete { response => {
            logger.info(Map(
              "worker.batchRequestLatency" -> Duration.between(start, Instant.now).toMillis,
              "notificationId" -> notificationId
            ), "Batch send request completed")
            parseBatchSendResponse(notificationId, tokens, response)(onAPICallComplete)
          }
        }
    }
  }

  def parseSendResponse(
    notificationId: UUID, token: String, response: Try[String]
  ): Either[DeliveryException, Success] = response match {
    case Success(messageId) =>
      Right(FcmDeliverySuccess(token, messageId))
    case Failure(UnwrappingExecutionException(e: FirebaseMessagingException)) if invalidTokenErrorCodes.contains(e.getErrorCode) || isUnregistered(e) =>
      Left(InvalidToken(notificationId, token, e.getMessage))
    case Failure(UnwrappingExecutionException(e: FirebaseMessagingException)) =>
      Left(FailedRequest(notificationId, token, e, Option(e.getErrorCode.toString)))
    case Failure(NonFatal(t)) =>
      Left(FailedRequest(notificationId, token, t))
    case Failure(_) =>
      Left(UnknownReasonFailedRequest(notificationId, token))
  }

  def parseBatchSendResponse(
    notificationId: UUID, tokens: List[String], triedResponse: Try[BatchResponse]
  )(cb: Either[DeliveryException, BatchSuccess] => Unit): Unit = triedResponse match {
    case Success(batchResponse) =>
      cb(Right(FcmBatchDeliverySuccess(
        // From firebase sdk docs: the order of the response list corresponds to the order of the input tokens
        batchResponse.getResponses.asScala.toList.zip(tokens).map { el => {
          val (r, token) = el
          if (!r.isSuccessful) {
            parseSendResponse(notificationId, token, Failure(r.getException))
          } else {
            Right(FcmDeliverySuccess(s"Token in batch response succeeded", token))
          }
        }
        }, notificationId.toString)))


    case Failure(x) =>
      cb(Left(BatchCallFailedRequest(notificationId, s"Multicast Async Response Failure: ${x.getCause}")))
  }

}

object FcmClient {
  def apply(config: FcmConfig, firebaseAppName: Option[String]): Try[FcmClient] = {
    Try {
      val firebaseOptions: FirebaseOptions = FirebaseOptions.builder()
          .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(config.serviceAccountKey.getBytes)))
          .setHttpTransport(new OkGoogleHttpTransport)
          .setConnectTimeout(10000) // 10 seconds
          .build
      firebaseAppName match {
        case None => FirebaseApp.initializeApp(firebaseOptions)
        case Some(name) => FirebaseApp.initializeApp(firebaseOptions, name)
      }
    }.map(app => new FcmClient(FirebaseMessaging.getInstance(app), app, config))
  }
}

object FirebaseHelpers {

  implicit class RichApiFuture[T](val af: ApiFuture[T]) extends AnyVal {
    def asScala(implicit ec: ExecutionContext): Future[T] = {
      val p = Promise[T]()
      ApiFutures.addCallback(af, new ApiFutureCallback[T] {
        def onFailure(t: Throwable): Unit = p failure t
        def onSuccess(result: T): Unit = p success result
      }, (command: Runnable) => ec.execute(command))
      p.future
    }
  }

}
