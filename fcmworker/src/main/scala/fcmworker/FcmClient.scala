package fcmworker

import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.Executor

import com.google.api.core.ApiFuture
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging.{FirebaseMessaging, FirebaseMessagingException, Message}
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import fcmworker.FcmClient.{FcmResponse, FcmSuccess, Token}
import fcmworker.models.FcmException.{FcmDryRun, FcmFailedRequest, FcmInvalidPayload, FcmInvalidToken}
import fcmworker.models.payload.FcmPayload
import fcmworker.models.{FcmConfig, FcmException}

import scala.concurrent.{ExecutionContextExecutor, Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class FcmClient private (firebaseMessaging: FirebaseMessaging, firebaseApp: FirebaseApp, config: FcmConfig) {

  private val invalidTokenErrorCodes = Seq(
    "messaging/invalid-registration-token",
    "messaging/registration-token-not-registered"
  )

  def close(): Unit = firebaseApp.delete()

  def sendNotification(notificationId: UUID, token: Token, payload: FcmPayload)
    (onComplete: FcmResponse => Unit)
    (implicit ec: ExecutionContextExecutor): Unit = {


    val message = Message
      .builder
      .setToken(token)
      .setAndroidConfig(payload.androidConfig)
      .build


    if(config.dryRun) { // Firebase has a dry run mode but in order to get the same behavior for both APNS and Firebase we don't send the request
      onComplete(Left(FcmDryRun(notificationId, token)))
    } else {
      import FirebaseHelpers._
      firebaseMessaging
        .sendAsync(message)
        .asScala
        .onComplete {
          _ match {
            case Success(messageId) =>
              onComplete(Right(FcmSuccess(token, messageId)))
            case Failure(e: FirebaseMessagingException) if invalidTokenErrorCodes.contains(e.getErrorCode) =>
              onComplete(Left(FcmInvalidToken(notificationId, token, e)))
            case Failure(NonFatal(t)) =>
              onComplete(Left(FcmFailedRequest(notificationId, token, t)))
          }
        }
    }
  }

}

object FcmClient {

  case class FcmSuccess(token: Token, messageId: MessageId)
  type Token = String
  type MessageId = String
  type FcmResponse = Either[FcmException, FcmSuccess]

  def apply(config: FcmConfig): Try[FcmClient] = {
    Try {
      val firebaseOptions: FirebaseOptions = new FirebaseOptions.Builder()
          .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(config.serviceAccountKey.getBytes)))
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
