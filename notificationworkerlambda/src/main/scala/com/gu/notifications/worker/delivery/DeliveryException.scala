package com.gu.notifications.worker.delivery

import java.time.LocalDateTime
import java.util.UUID

sealed trait DeliveryException extends RuntimeException {
  def notificationId: UUID
}

object DeliveryException {

  case class GenericFailure(
    notificationId: UUID,
    token: String,
    underlying: Throwable
  ) extends DeliveryException {
    override def getMessage: String = s"Error (Notification: $notificationId, Token: $token, Underlying: $underlying)"
  }

  case class FailedDelivery(
    notificationId: UUID,
    token: String,
    reason: String
  ) extends DeliveryException {
    override def getMessage: String = s"Delivery failed (Notification: $notificationId, Token: $token, Reason: $reason)"
  }

  case class InvalidToken(
    notificationId: UUID,
    token: String,
    reason: String,
    tokenInvalidationTimestamp: Option[LocalDateTime] = None
  ) extends DeliveryException {
    override def getMessage: String =
        s"Delivery failed: Invalid token. Notification: $notificationId, Token: $token, Reason: $reason" +
        tokenInvalidationTimestamp.map(ts => s", Token invalidation timestamp: $ts")
  }

  case class FailedRequest(notificationId: UUID, token: String, cause: Throwable, errorCode: Option[String] = None) extends DeliveryException {
    override def getMessage = s"Request failed (Notification: $notificationId, Token: $token). Cause: ${cause.getMessage}. ErrorCode: $errorCode}."
  }

  case class UnknownReasonFailedRequest(notificationId: UUID, token: String) extends DeliveryException {
    override def getMessage = s"Request failed (Notification: $notificationId, Token: $token)"
  }


  case class InvalidPayload(notificationId: UUID) extends DeliveryException {
    override def getMessage = s"Cannot generate payload (Notification: $notificationId)"
  }

  case class InvalidTopics(notificationId: UUID) extends DeliveryException {
    override def getMessage = s"No topic (Notification: $notificationId)"
  }

}

