package fcmworker.models

import java.util.UUID

import fcmworker.FcmClient.Token

sealed trait FcmException extends RuntimeException {
  def notificationId: UUID
  def prefix: String = "FCM - "
}

object FcmException {

  case class FcmGenericFailure(notificationId: UUID, token: Token, cause: Throwable) extends FcmException {
    override def getMessage: String =
      prefix + s"Error (Notification: $notificationId, Token: $token, Cause: ${cause.getMessage})"
  }

  case class FcmInvalidToken(notificationId: UUID, token: String, cause: Throwable) extends FcmException {
      override def getMessage: String =
        prefix + s"Delivery failed: Invalid token (Notification: $notificationId, Token: $token, Cause: ${cause.getMessage})"
  }

  case class FcmFailedRequest(notificationId: UUID, token: String, cause: Throwable) extends FcmException {
    override def getMessage =
      prefix + s"Request failed (Notification: $notificationId, Token: $token). Cause: ${cause.getMessage}"
  }

  case class FcmDryRun(notificationId: UUID, token: String) extends FcmException {
    override def getMessage =
      prefix + s"DRY RUN !!!! Notification has not be sent (Notification: $notificationId, Token: $token)}"
  }

  case class FcmInvalidPayload(notificationId: UUID) extends FcmException {
    override def getMessage = prefix + s"Cannot generate payload (Notification: $notificationId)"
  }

  case class FcmInvalidTopics(notificationId: UUID) extends FcmException {
    override def getMessage = prefix + s"No topic (Notification: $notificationId)"
  }

}


