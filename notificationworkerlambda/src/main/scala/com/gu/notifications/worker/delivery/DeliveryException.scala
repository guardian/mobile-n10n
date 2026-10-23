package com.gu.notifications.worker.delivery

import java.time.LocalDateTime
import java.util.UUID
import scala.util.control.NoStackTrace

sealed trait DeliveryException extends NoStackTrace {
  def notificationId: UUID
}

object DeliveryException {

  case class GenericFailure(
    notificationId: UUID,
    token: String,
    underlying: Throwable
  ) extends DeliveryException {
    override def getMessage: String = s"Error (Notification: $notificationId, Underlying: $underlying)"
  }

  case class FailedAPNSDelivery(
    notificationId: UUID,
    token: String,
    reason: String
  ) extends DeliveryException {
    override def getMessage: String = s"APNS Delivery failed (Notification: $notificationId, Reason: $reason)"
  }

  case class InvalidToken(
    notificationId: UUID,
    token: String,
    reason: String,
    tokenInvalidationTimestamp: Option[LocalDateTime] = None
  ) extends DeliveryException {
    override def getMessage: String =
      s"Delivery failed: Invalid token. Notification: $notificationId, Reason: $reason" +
        tokenInvalidationTimestamp.map(ts => s", Token invalidation timestamp: $ts").getOrElse("")
  }

  case class FailedRequest(notificationId: UUID, token: String, cause: Throwable, errorCode: Option[String] = None) extends DeliveryException {
    override def getMessage = s"Request failed (Notification: $notificationId). Cause: ${cause.getMessage}. ErrorCode: $errorCode}."
  }

  case class UnknownReasonFailedRequest(notificationId: UUID, token: String) extends DeliveryException {
    override def getMessage = s"Request failed (Notification: $notificationId)"
  }

  case class InvalidPayload(notificationId: UUID) extends DeliveryException {
    override def getMessage = s"Cannot generate payload (Notification: $notificationId)"
  }

  case class InvalidTopics(notificationId: UUID) extends DeliveryException {
    override def getMessage = s"No topic (Notification: $notificationId)"
  }

}

