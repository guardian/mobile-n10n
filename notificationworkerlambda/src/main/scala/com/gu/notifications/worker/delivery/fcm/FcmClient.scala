package com.gu.notifications.worker.delivery.fcm

import _root_.models.Notification
import com.google.api.core.{ApiFuture, ApiFutureCallback, ApiFutures}
import com.google.api.client.json.JsonFactory
import com.google.auth.oauth2.{GoogleCredentials, ServiceAccountCredentials}
import com.google.firebase.messaging._
import com.google.firebase.{ErrorCode, FirebaseApp, FirebaseOptions}
import com.gu.notifications.worker.delivery.DeliveryException.{FailedRequest, InvalidToken, UnknownReasonFailedRequest}
import com.gu.notifications.worker.delivery.fcm.models.FcmConfig
import com.gu.notifications.worker.delivery.fcm.models.payload.{FcmErrorPayload, FcmPayloadBuilder}
import com.gu.notifications.worker.delivery.fcm.oktransport.OkGoogleHttpTransport
import com.gu.notifications.worker.delivery.{DeliveryClient, DeliveryException, FcmDeliverySuccess, FcmPayload}
import com.gu.notifications.worker.utils.Logging
import org.slf4j.{Logger, LoggerFactory}


import java.io.ByteArrayInputStream
import java.time.{Duration, Instant}
import java.util.UUID
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise}
import scala.util.{Failure, Success, Try}

class FcmClient (firebaseMessaging: FirebaseMessaging, firebaseApp: FirebaseApp, config: FcmConfig, projectId: String, credential: GoogleCredentials, jsonFactory: JsonFactory)
  extends DeliveryClient with Logging {

  type Success = FcmDeliverySuccess
  type Payload = FcmPayload
  val dryRun = config.dryRun

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private val invalidTokenErrorCodes = Set(
    MessagingErrorCode.INVALID_ARGUMENT,
    MessagingErrorCode.UNREGISTERED,
    MessagingErrorCode.SENDER_ID_MISMATCH,
    ErrorCode.PERMISSION_DENIED
  )

  private final val FCM_URL: String = s"https://fcm.googleapis.com/v1/projects/${projectId}/messages:send";

  private val fcmTransport: FcmTransport = new FcmTransportJdkImpl(credential, FCM_URL, jsonFactory, config.connectTimeout, config.requestTimeout)

  def payloadBuilder: Notification => Option[FcmPayload] = n => FcmPayloadBuilder(n, config.debug)

  def isUnregistered(e: FirebaseMessagingException): Boolean = {
    (e.getErrorCode, e.getMessage) match {
      case (ErrorCode.NOT_FOUND, "Requested entity was not found.") => true
      case (_, _) => false
    }
  }

  private def errorDetailFields(details: FcmErrorPayload): Map[String, Any] = Map(
    "fcm.errorCode" -> details.fcmErrorCode.getOrElse(details.status),
    "fcm.errorMessage" -> details.message,
  )

  private def exceptionFields(e: Throwable): Map[String, Any] = Map(
    "fcm.errorType" -> e.getClass.getSimpleName,
    "fcm.errorMessage" -> Option(e.getMessage).getOrElse(""),
  )

  private def responseFields(response: Try[String]): Map[String, Any] = response match {
    case Success(messageId) => Map("fcm.outcome" -> "accepted", "fcm.messageId" -> messageId)
    case Failure(e: InvalidTokenException) => Map("fcm.outcome" -> "invalidToken") ++ errorDetailFields(e.details)
    case Failure(e: FcmServerException) => Map("fcm.outcome" -> "rejected") ++ errorDetailFields(e.details)
    case Failure(e: QuotaExceededException) => Map("fcm.outcome" -> "rejected") ++ errorDetailFields(e.details)
    case Failure(e: UnknownException) => Map("fcm.outcome" -> "rejected") ++ errorDetailFields(e.details)
    case Failure(e: InvalidResponseException) => Map("fcm.outcome" -> "invalidResponse") ++ exceptionFields(e)
    case Failure(e: FcmServerTransportException) => Map("fcm.outcome" -> "requestFailed") ++ exceptionFields(Option(e.ex).getOrElse(e))
    case Failure(e) => Map("fcm.outcome" -> "requestFailed") ++ exceptionFields(e)
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
      onAPICallComplete(Right(FcmDeliverySuccess(token, "dryrun", Instant.now(), dryRun = true)))
    } else {
      import FirebaseHelpers._
      val start = Instant.now
      fcmTransport.sendAsync(token, payload, dryRun)
        .onComplete { response =>
          val requestCompletionTime = Instant.now
          logger.info(Map(
            "worker.individualRequestLatency" -> Duration.between(start, requestCompletionTime).toMillis,
            "notificationId" -> notificationId,
            "fcm.clientInstance" -> this.toString,
          ) ++ responseFields(response), "Individual send request completed")
          onAPICallComplete(parseSendResponse(notificationId, token, response, requestCompletionTime))
        }
    }
  }
 
  def parseSendResponse(
    notificationId: UUID, token: String, response: Try[String], requestCompletionTime: Instant
  ): Either[DeliveryException, Success] = response match {
    case Success(messageId) =>
      Right(FcmDeliverySuccess(token, messageId, requestCompletionTime))
    case Failure(e: InvalidTokenException) =>
      Left(InvalidToken(notificationId, token, e.details.fcmErrorCode.getOrElse(e.details.status)))
    case Failure(e: FcmServerException) =>
      Left(FailedRequest(notificationId, token, e, Some(e.details.fcmErrorCode.getOrElse(e.details.status))))
    case Failure(e: UnknownException) =>
      Left(FailedRequest(notificationId, token, e, Some(e.details.fcmErrorCode.getOrElse(e.details.status))))
    case Failure(e: InvalidResponseException) =>
      Left(FailedRequest(notificationId, token, e, None))
    case Failure(e: QuotaExceededException) =>
      Left(FailedRequest(notificationId, token, e, None))
    case Failure(e: FcmServerTransportException) =>
      Left(FailedRequest(notificationId, token, e, None))
    case Failure(_) =>
      Left(UnknownReasonFailedRequest(notificationId, token))
  }
}

case class FcmFirebase(firebaseMessaging: FirebaseMessaging, firebaseApp: FirebaseApp, config: FcmConfig, projectId: String, credential: GoogleCredentials, jsonFactory: JsonFactory)

object FcmFirebase {
  def apply(config: FcmConfig, firebaseAppName: Option[String]): Try[FcmFirebase] =
    Try {
      val credential = GoogleCredentials.fromStream(new ByteArrayInputStream(config.serviceAccountKey.getBytes))
      val firebaseOptions: FirebaseOptions = FirebaseOptions.builder()
          .setCredentials(credential)
          .setHttpTransport(new OkGoogleHttpTransport)
          .setConnectTimeout(10000) // 10 seconds
          .build
      val firebaseApp = firebaseAppName match {
        case None => FirebaseApp.initializeApp(firebaseOptions)
        case Some(name) => FirebaseApp.initializeApp(firebaseOptions, name)
      }
      val projectId = credential match {
        case s: ServiceAccountCredentials => s.getProjectId()
        case _ => ""
      }
      new FcmFirebase(FirebaseMessaging.getInstance(firebaseApp), firebaseApp, config, projectId, credential, firebaseOptions.getJsonFactory())
    }
}

object FcmClient {
  def apply(firebase: FcmFirebase): FcmClient =
      new FcmClient(firebase.firebaseMessaging, firebase.firebaseApp, firebase.config, firebase.projectId, firebase.credential, firebase.jsonFactory)
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
