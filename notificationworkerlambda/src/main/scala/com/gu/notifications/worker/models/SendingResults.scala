package com.gu.notifications.worker.models

import com.gu.notifications.worker.delivery.{BatchDeliverySuccess, DeliveryException, DeliverySuccess}

import java.time.{Duration, Instant}

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

  def aggregateBatch(previous: SendingResults, batchSize: Int, res: Either[Throwable, BatchDeliverySuccess]): SendingResults = res match {
    case Right(batchSuccess) =>
      batchSuccess.responses.foldLeft(previous)((acc, resp) => SendingResults.aggregate(acc, resp))
    case Left(_) => SendingResults(previous.successCount, previous.failureCount + batchSize, previous.dryRunCount)
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
  notificationProcessingEndTime: Long,
  sqsMessageBatchSize: Int,
  chunkTokenSize: Int,
)

case class LatencyMetrics(
  tokenDeliveriesWithin10: Int,
  tokenDeliveriesWithin20: Int,
  totalTokenDeliveries: Int
)

object LatencyMetrics {

  def empty = LatencyMetrics(tokenDeliveriesWithin10 = 0, tokenDeliveriesWithin20 = 0, totalTokenDeliveries = 0)

  def aggregateBatchLatency(previous: LatencyMetrics, result: Either[Throwable, BatchDeliverySuccess], notificationSentTime: Instant): LatencyMetrics = {
    result.toOption.flatMap { batchSuccess =>
      val successes = batchSuccess.responses.collect { case Right(success) => success }
      successes.headOption.map { firstDelivery =>
        val successfulDeliveries = successes.size
        val timeOfDeliveries: Instant = firstDelivery.deliveryTime
        Duration.between(notificationSentTime, timeOfDeliveries).getSeconds match {
          case seconds if seconds <= 10 => previous.copy(
            tokenDeliveriesWithin10 = previous.tokenDeliveriesWithin10 + successfulDeliveries,
            tokenDeliveriesWithin20 = previous.tokenDeliveriesWithin20 + successfulDeliveries,
            totalTokenDeliveries = previous.totalTokenDeliveries + successfulDeliveries
          )
          case seconds if seconds <= 20 => previous.copy(
            tokenDeliveriesWithin20 = previous.tokenDeliveriesWithin20 + successfulDeliveries,
            totalTokenDeliveries = previous.totalTokenDeliveries + successfulDeliveries
          )
          case _ => previous.copy(
            totalTokenDeliveries = previous.totalTokenDeliveries + successfulDeliveries
          )
        }
      }
    }.getOrElse(previous) // If the overall batch was a failure (or the batch only contained failures) just return the previous result
  }
}

