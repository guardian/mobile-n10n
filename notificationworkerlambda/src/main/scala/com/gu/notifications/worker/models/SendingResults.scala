package com.gu.notifications.worker.models

import com.gu.notifications.worker.delivery.{DeliveryException, DeliverySuccess}

case class SendingResults(
  successCount: Int,
  failureCount: Int,
  dryRunCount: Int
) {
  def total: Int = successCount + failureCount + dryRunCount
  override def toString: String =
    s"Success: $successCount, Failure: $failureCount, DryRun: $dryRunCount Total: $total"
}

object SendingResults {
  def empty = new SendingResults(0, 0, 0)

  def aggregate(previous: SendingResults, res: Either[DeliveryException, DeliverySuccess]) = res match {
    case Right(success) if success.dryRun => previous.copy(dryRunCount = previous.dryRunCount + 1)
    case Right(_) => previous.copy(successCount = previous.successCount + 1)
    case Left(_) => previous.copy(failureCount = previous.failureCount + 1)
  }
}

case class PerformanceMetrics(
  notificationId: String,
  platform: String,
  notificationType: String,
  functionProcessingRate: Double,
  functionProcessingTime: Long,
  notificationProcessingTime: Long,
  notificationProcessingStartTime: Long,
  notificationProcessingEndTime: Long
)

