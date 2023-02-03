package com.gu.notifications.worker.models

import com.gu.notifications.worker.delivery.{BatchDeliverySuccess, DeliveryException, DeliverySuccess}
import org.slf4j.{Logger, LoggerFactory}

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

case class LatencyMetricsForCloudWatch(uniqueValues: List[Long], orderedCounts: List[Int])

object LatencyMetrics {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def aggregateForCloudWatch(allTokenDeliveryLatencies: List[Long], batchSize: Int = 150): List[LatencyMetricsForCloudWatch] = {
    val uniqueLatencies = allTokenDeliveryLatencies.distinct
    val countsForEachLatency = allTokenDeliveryLatencies.groupBy(identity).view.mapValues(_.size)
    val orderedCounts = uniqueLatencies.map(value => countsForEachLatency(value))
    val aggregatedMetrics = uniqueLatencies.grouped(batchSize).toList.zipWithIndex.map { case (uniqueValueBatch, index) =>
      val orderedCountsForBatch = orderedCounts.grouped(batchSize).toList(index)
      LatencyMetricsForCloudWatch(uniqueValueBatch, orderedCountsForBatch)
    }
    logger.info(s"Aggregated the following latency metrics for CloudWatch: ${aggregatedMetrics}")
    aggregatedMetrics
  }

  def collectLatency(previous: List[Long], result: Either[Throwable, DeliverySuccess], notificationSentTime: Instant): List[Long] = {
    result.map { successfulDelivery =>
      val timeOfDeliveries: Instant = successfulDelivery.deliveryTime
      val duration = Duration.between(notificationSentTime, timeOfDeliveries).toSeconds
      previous :+ duration
    }.getOrElse(previous) // If the delivery was a failure just return the previous result
  }

  def collectBatchLatency(previous: List[Long], result: Either[Throwable, BatchDeliverySuccess], notificationSentTime: Instant): List[Long] = {
    result.toOption.flatMap { batchSuccess =>
      val successes = batchSuccess.responses.collect { case Right(success) => success }
      successes.headOption.map { firstDelivery =>
        val successfulDeliveries = successes.size
        val timeOfDeliveries: Instant = firstDelivery.deliveryTime
        val duration = Duration.between(notificationSentTime, timeOfDeliveries).toSeconds
        // The batch are all delivered at the same time so we dont need to recalculate for every delivery in the batch.
        // However we do want to record a time for each delivery.
        val batchDeliveryTimes = List.fill(successfulDeliveries)(duration)
        previous ++ batchDeliveryTimes
      }
    }.getOrElse(previous) // If the overall batch was a failure (or the batch only contained failures) just return the previous result
  }

  def audienceSizeBucket(audienceSize: Option[Int]): String = audienceSize match {
    case None => "unknown"
    case Some(audience) if audience < 100000 => "S"
    case Some(audience) if audience < 500000 => "M"
    case Some(audience) if audience < 1000000 => "L"
    case _ => "XL"
  }

}

