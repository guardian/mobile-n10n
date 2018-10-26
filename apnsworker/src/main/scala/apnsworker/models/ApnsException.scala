package apnsworker.models

import java.time.LocalDateTime
import java.util.UUID


sealed trait ApnsException extends RuntimeException {
  def notificationId: UUID
  def token: String
}

object ApnsException {

  case class ApnsFailedDelivery(
    notificationId: UUID,
    token: String,
    reason: String
  ) extends ApnsException {
    override def getMessage: String = s"Sending notification '$notificationId' to device '$token' failed. Reason: $reason."
  }

  case class ApnsInvalidToken(
    notificationId: UUID,
    token: String,
    reason: String,
    tokenInvalidationTimestamp: LocalDateTime
  ) extends ApnsException {
      override def getMessage: String =
        s"Sending notification '$notificationId' to device '$token' failed. Reason: $reason. Token invalidation timestamp: $tokenInvalidationTimestamp"
  }

  case class ApnsFailedRequest(notificationId: UUID, token: String, cause: Throwable) extends ApnsException {
    override def getMessage = s"Error: APNS request failed (Notification $notificationId, Token: $token). Cause: ${cause.getMessage}"
  }

  case class ApnsDryRun(notificationId: UUID, token: String) extends ApnsException {
    override def getMessage = s"Dry RUN !!!! Notification has not be sent (Notification $notificationId, Token: $token)}"
  }

}


