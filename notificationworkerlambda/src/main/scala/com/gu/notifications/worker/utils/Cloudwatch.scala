package com.gu.notifications.worker.utils

import cats.effect.IO
import com.amazonaws.internal.SdkInternalList
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}
import com.amazonaws.services.cloudwatch.{AmazonCloudWatch, AmazonCloudWatchClientBuilder}
import com.gu.notifications.worker.models.{LatencyMetrics, PerformanceMetrics, SendingResults}
import fs2.Pipe
import models.Platform

import scala.jdk.CollectionConverters._
import models.Notification
import models.NotificationType

import java.time.Instant
import java.time.Duration

trait Cloudwatch {
  def sendResults(stage: String, platform: Option[Platform]): Pipe[IO, SendingResults, Unit]
  def sendPerformanceMetrics(stage: String, enablePerformanceMetric: Boolean): PerformanceMetrics => Unit

  def sendLatencyMetrics(shouldPushMetricsToAws: Boolean, stage: String, platform: Option[Platform]): Pipe[IO, List[Long], Unit]
  def sendFailures(stage: String, platform: Platform): Pipe[IO, Throwable, Unit]
}

class CloudwatchImpl(val senderMetricNs: String) extends Cloudwatch {

  lazy val cloudwatchClient: AmazonCloudWatch = AmazonCloudWatchClientBuilder
    .standard()
    .withCredentials(Aws.credentialsProvider)
    .withRegion(Regions.EU_WEST_1)
    .build

  private def countDatum(name: String, value: Int, dimension: Dimension) =
    new MetricDatum()
      .withMetricName(name)
      .withUnit(StandardUnit.None)
      .withValue(value.toDouble)
      .withDimensions(dimension)

  private def latencyDatum(name: String, values: List[Long], counts: List[Int], dimension: Dimension) = {
    val valuesAsJava = values.map(value => Double.box(value.toDouble)).asJava
    val countsAsJava = counts.map(count => Double.box(count.toDouble)).asJava
    new MetricDatum()
      .withMetricName(name)
      .withUnit(StandardUnit.Seconds)
      .withValues(valuesAsJava)
      .withCounts(countsAsJava)
      .withDimensions(dimension)
  }

  private def perfMetricDatum(name: String, unit: StandardUnit, value: Double) =
    new MetricDatum()
      .withMetricName(name)
      .withUnit(unit)
      .withValue(value)

  def sendResults(stage: String, platform: Option[Platform]): Pipe[IO, SendingResults, Unit] = _.evalMap { results =>
    IO.delay {
      val dimension = new Dimension().withName("platform").withValue(platform.map(_.toString).getOrElse("unknown"))
      val metrics: Seq[MetricDatum] = Seq(
        countDatum("success", results.successCount, dimension),
        countDatum("failure", results.failureCount, dimension),
        countDatum("dryrun", results.dryRunCount, dimension),
        countDatum("total", results.total, dimension)
      )
      val req = new PutMetricDataRequest()
        .withNamespace(s"Notifications/$stage/$senderMetricNs")
        .withMetricData(metrics.asJava)
      cloudwatchClient.putMetricData(req)
      ()
    }
  }

  def sendLatencyMetrics(shouldPushMetricsToAws: Boolean, stage: String, platform: Option[Platform]): Pipe[IO, List[Long], Unit] = _.evalMap { deliveryTimes =>
    IO.delay {
      val uniqueValues = deliveryTimes.distinct
      val countsForEachValue = deliveryTimes.groupBy(identity).view.mapValues(_.size)
      val orderedCounts = uniqueValues.map(value => countsForEachValue(value))
      val dimension = new Dimension().withName("platform").withValue(platform.map(_.toString).getOrElse("unknown"))
      // TODO: split into batches of 150 unique values: https://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/API_MetricDatum.html
      val cloudWatchMetric: MetricDatum = latencyDatum(name = "NotificationDeliveryLatency", dimension = dimension, values = uniqueValues, counts = orderedCounts)
      val req = new PutMetricDataRequest()
        .withNamespace(s"Notifications/$stage/$senderMetricNs")
        .withMetricData(cloudWatchMetric)
      if (shouldPushMetricsToAws) {
        cloudwatchClient.putMetricData(req)
      }
      ()
    }
  }

  def sendPerformanceMetrics(stage: String, enablePerformanceMetric: Boolean): PerformanceMetrics => Unit = performanceData =>
    if (enablePerformanceMetric) {
        val dimension1 = new Dimension().withName("platform").withValue(performanceData.platform)
        val dimension2 = new Dimension().withName("type").withValue(performanceData.notificationType)
        val perfMetrics: Seq[MetricDatum] = Seq(
          perfMetricDatum("worker.notificationProcessingTime", StandardUnit.Milliseconds, performanceData.notificationProcessingTime.toDouble).withDimensions(dimension1, dimension2),
          perfMetricDatum("worker.functionProcessingRate", StandardUnit.None, performanceData.functionProcessingRate).withDimensions(dimension1, dimension2),
        )
        val req = new PutMetricDataRequest()
          .withNamespace(s"Notifications/$stage/$senderMetricNs")
          .withMetricData(perfMetrics.asJava)
        cloudwatchClient.putMetricData(req)
        ()
    } else {
      ()
    }

  def sendFailures(stage: String, platform: Platform): Pipe[IO, Throwable, Unit] = input => input.fold(0) {
    case (count, _) => count + 1
  }.evalMap { count =>
    IO.delay {
      cloudwatchClient.putMetricData(
        new PutMetricDataRequest()
          .withNamespace(s"Notifications/$stage/harvester")
          .withMetricData(Seq(countDatum(
            "failure",
            count,
            new Dimension()
              .withName("platform")
              .withValue(platform.toString))).asJava))
      ()
    }
  }
}
