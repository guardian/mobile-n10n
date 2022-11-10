package com.gu.notifications.worker.utils

import cats.effect.IO
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}
import com.amazonaws.services.cloudwatch.{AmazonCloudWatch, AmazonCloudWatchClientBuilder}
import com.gu.notifications.worker.models.SendingResults
import fs2.Pipe
import models.Platform

import scala.jdk.CollectionConverters._
import models.Notification
import models.NotificationType
import java.time.Instant
import java.time.Duration
import com.gu.notifications.worker.models.PerformanceMetrics

trait Cloudwatch {
  def sendMetrics(stage: String, platform: Option[Platform]): Pipe[IO, SendingResults, Unit]
  def sendPerformanceMetrics(stage: String, enablePerformanceMetric: Boolean): PerformanceMetrics => Unit
  def sendFailures(stage: String, platform: Platform): Pipe[IO, Throwable, Unit]
}

class CloudwatchImpl extends Cloudwatch {

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

  private def perfMetricDatum(name: String, unit: StandardUnit, value: Double) =
    new MetricDatum()
      .withMetricName(name)
      .withUnit(unit)
      .withValue(value)

  def sendMetrics(stage: String, platform: Option[Platform]): Pipe[IO, SendingResults, Unit] = _.evalMap { results =>
    IO.delay {
      val dimension = new Dimension().withName("platform").withValue(platform.map(_.toString).getOrElse("unknown"))
      val metrics: Seq[MetricDatum] = Seq(
        countDatum("success", results.successCount, dimension),
        countDatum("failure", results.failureCount, dimension),
        countDatum("dryrun", results.dryRunCount, dimension),
        countDatum("total", results.total, dimension)
      )
      val req = new PutMetricDataRequest()
        .withNamespace(s"Notifications/$stage/workers")
        .withMetricData(metrics.asJava)
      cloudwatchClient.putMetricData(req)
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
          .withNamespace(s"Notifications/$stage/workers")
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
