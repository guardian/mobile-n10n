package com.gu.notifications.worker.utils

import cats.effect.IO
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}
import com.gu.notifications.worker.models.{LatencyMetrics, PerformanceMetrics, SendingResults}
import fs2.Pipe
import models.Platform

import scala.jdk.CollectionConverters._

trait Cloudwatch {
  def sendResults(stage: String, platform: Option[Platform]): Pipe[IO, SendingResults, Unit]
  def sendPerformanceMetrics(stage: String, enablePerformanceMetric: Boolean): PerformanceMetrics => Unit

  def sendLatencyMetrics(shouldPushMetricsToAws: Boolean, stage: String, platform: Option[Platform], audienceSize: Option[Int]): Pipe[IO, List[Long], Unit]
  def sendFailures(stage: String, platform: Platform): Pipe[IO, Throwable, Unit]
}

class CloudwatchImpl(val senderMetricNs: String) extends Cloudwatch {

  lazy val cloudwatchClient: CloudWatchClient = CloudWatchClient
    .builder()
    .credentialsProvider(Aws.credentialsProviderV2)
    .region(Region.EU_WEST_1)
    .build()

  private def countDatum(name: String, value: Int, dimension: Dimension) =
    MetricDatum.builder()
      .metricName(name)
      .unit(StandardUnit.NONE)
      .value(Double.box(value.toDouble))
      .dimensions(dimension)
      .build()

  private def latencyDatum(name: String, values: List[Long], counts: List[Int], dimensions: List[Dimension]) = {
    val valuesAsJava = values.map(value => Double.box(value.toDouble)).asJava
    val countsAsJava = counts.map(count => Double.box(count.toDouble)).asJava
    MetricDatum.builder()
      .metricName(name)
      .unit(StandardUnit.SECONDS)
      .values(valuesAsJava)
      .counts(countsAsJava)
      .dimensions(dimensions.asJava)
      .build()
  }

  private def perfMetricDatum(name: String, unit: StandardUnit, value: Double) =
    MetricDatum.builder()
      .metricName(name)
      .unit(unit)
      .value(Double.box(value))
      .build()

  def sendResults(stage: String, platform: Option[Platform]): Pipe[IO, SendingResults, Unit] = _.evalMap { results =>
    IO.delay {
      val dimension = Dimension.builder().name("platform").value(platform.map(_.toString).getOrElse("unknown")).build()
      val metrics: Seq[MetricDatum] = Seq(
        countDatum("success", results.successCount, dimension),
        countDatum("failure", results.failureCount, dimension),
        countDatum("dryrun", results.dryRunCount, dimension),
        countDatum("total", results.total, dimension)
      )
      val req = PutMetricDataRequest.builder()
        .namespace(s"Notifications/$stage/$senderMetricNs")
        .metricData(metrics.asJava)
        .build()
      cloudwatchClient.putMetricData(req)
      ()
    }
  }

  def sendLatencyMetrics(shouldPushMetricsToAws: Boolean, stage: String, platform: Option[Platform], audienceSize: Option[Int]): Pipe[IO, List[Long], Unit] = _.evalMap { deliveryTimes =>
    IO.delay {
      val latencyMetrics = LatencyMetrics.aggregateForCloudWatch(deliveryTimes)
      val platformDimension = Dimension.builder().name("platform").value(platform.map(_.toString).getOrElse("unknown")).build()
      val audienceSizeDimension = Dimension.builder().name("audienceSize").value(LatencyMetrics.audienceSizeBucket(audienceSize)).build()
      val requests = latencyMetrics.map { valuesAndCounts =>
        val cloudWatchMetric: MetricDatum = latencyDatum("TokenDeliveryLatency", valuesAndCounts.uniqueValues, valuesAndCounts.orderedCounts, List(platformDimension, audienceSizeDimension))
        PutMetricDataRequest.builder()
          .namespace(s"Notifications/$stage/$senderMetricNs")
          .metricData(cloudWatchMetric)
          .build()
      }
      if (shouldPushMetricsToAws && latencyMetrics.exists(_.uniqueValues.nonEmpty)) {
        requests.map(req => cloudwatchClient.putMetricData(req))
      }
      ()
    }
  }

  def sendPerformanceMetrics(stage: String, enablePerformanceMetric: Boolean): PerformanceMetrics => Unit = performanceData =>
    if (enablePerformanceMetric) {
        val dimension1 = Dimension.builder().name("platform").value(performanceData.platform).build()
        val dimension2 = Dimension.builder().name("type").value(performanceData.notificationType).build()
        val perfMetrics: Seq[MetricDatum] = Seq(
          perfMetricDatum("worker.notificationProcessingTime", StandardUnit.MILLISECONDS, performanceData.notificationProcessingTime.toDouble).toBuilder.dimensions(dimension1, dimension2).build(),
          perfMetricDatum("worker.functionProcessingRate", StandardUnit.NONE, performanceData.functionProcessingRate).toBuilder.dimensions(dimension1, dimension2).build(),
        )
        val req = PutMetricDataRequest.builder()
          .namespace(s"Notifications/$stage/$senderMetricNs")
          .metricData(perfMetrics.asJava)
          .build()
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
        PutMetricDataRequest.builder()
          .namespace(s"Notifications/$stage/harvester")
          .metricData(Seq(countDatum(
            "failure",
            count,
            Dimension.builder()
              .name("platform")
              .value(platform.toString).build())).asJava)
          .build())
      ()
    }
  }
}
