package com.gu.notifications.worker.utils

import com.codahale.metrics.Counter
import com.gu.notifications.worker.delivery.apns.models.IOSMetricsRegistry

import java.time.{Duration, Instant}
import scala.jdk.CollectionConverters.MapHasAsScala

trait IOSLogging extends Logging {
  override def logEndOfInvocation(
    sqsMessageBatchSize: Int,
    totalTokensProcessed: Int,
    startTime: Instant,
    registry: IOSMetricsRegistry
  ): Unit = {
    val metricMap = registry.registry.getMetrics.asScala.toMap.map(thing => {
      thing._2 match {
        case counter: Counter => (thing._1, counter.getCount)
      }
    })

    logger.info(metricMap ++ Map(
      "sqsMessageBatchSize" -> sqsMessageBatchSize,
      "totalTokensProcessed" -> totalTokensProcessed,
      "invocation.functionProcessingRate" -> {
        totalTokensProcessed.toDouble / Duration.between(startTime, Instant.now).toMillis * 1000
      },
    ), "Processed all ios sqs messages from sqs event")
  }
}
