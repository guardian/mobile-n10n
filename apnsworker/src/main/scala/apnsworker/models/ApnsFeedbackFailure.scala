package apnsworker.models

import java.time.LocalDateTime
import java.util.UUID


case class ApnsFeedbackFailure(
  token: String,
  apnsId: UUID,
  reason: String,
  tokenInvalidationTimestamp: Option[LocalDateTime]
) extends RuntimeException {
  override def toString: String = {
    s"Sending notification '$apnsId' to device '$token' failed. Reason: $reason." +
      tokenInvalidationTimestamp.map(ts => " Token invalidation timestamp: $ts").getOrElse("")
  }
}

