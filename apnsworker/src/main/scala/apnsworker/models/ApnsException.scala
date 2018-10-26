package apnsworker.models

import java.time.LocalDateTime
import java.util.UUID


sealed trait ApnsException extends RuntimeException {
  def notificationId: UUID
  def prefix: String = "APNS: "
}

object ApnsException {

  case class ApnsGenericFailure(
    notificationId: UUID,
    token: String,
    underlying: Throwable
  ) extends ApnsException {
    override def getMessage: String = prefix + s"Error (Notification: $notificationId, Token: $token, Underlying: $underlying)"
  }

  case class ApnsFailedDelivery(
    notificationId: UUID,
    token: String,
    reason: String
  ) extends ApnsException {
    override def getMessage: String = prefix + s"Delivery failed (Notification: $notificationId, Token: $token, Reason: $reason)"
  }

  case class ApnsInvalidToken(
    notificationId: UUID,
    token: String,
    reason: String,
    tokenInvalidationTimestamp: LocalDateTime
  ) extends ApnsException {
      override def getMessage: String =
        prefix + s"Delivery failed: Invalid token (Notification: $notificationId, Token: $token, Reason: $reason, Token invalidation timestamp: $tokenInvalidationTimestamp)"
  }

  case class ApnsFailedRequest(notificationId: UUID, token: String, cause: Throwable) extends ApnsException {
    override def getMessage = prefix + s"Request failed (Notification: $notificationId, Token: $token). Cause: ${cause.getMessage}"
  }

  case class ApnsDryRun(notificationId: UUID, token: String) extends ApnsException {
    override def getMessage = prefix + s"DRY RUN !!!! Notification has not be sent (Notification: $notificationId, Token: $token)}"
  }

  case class ApnsInvalidPayload(notificationId: UUID) extends ApnsException {
    override def getMessage = prefix + s"Cannot generate payload (Notification: $notificationId)"
  }

  case class ApnsInvalidTopics(notificationId: UUID) extends ApnsException {
    override def getMessage = prefix + s"No topic (Notification: $notificationId)"
  }

}


