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

trait Cloudwatch {
  def sendMetrics(stage: String, platform: Option[Platform],
                  notification: Notification,
                  numberOfTokens: Int,
                  sentTime: Long,
                  functionStartTime: Instant): Pipe[IO, SendingResults, Unit]
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

  private def metricDatum(name: String, unit: StandardUnit, value: Double, dimension: Dimension) =
    new MetricDatum()
      .withMetricName(name)
      .withUnit(unit)
      .withValue(value)
      .withDimensions(dimension)

  def sendMetrics(stage: String, platform: Option[Platform],
                  notification: Notification,
                  numberOfTokens: Int,
                  sentTime: Long,
                  functionStartTime: Instant): Pipe[IO, SendingResults, Unit] = _.evalMap { results =>
    IO.delay {
      val dimension = new Dimension().withName("platform").withValue(platform.map(_.toString).getOrElse("unknown"))
      val metrics: Seq[MetricDatum] = Seq(
        countDatum("success", results.successCount, dimension),
        countDatum("failure", results.failureCount, dimension),
        countDatum("dryrun", results.dryRunCount, dimension),
        countDatum("total", results.total, dimension)
      )

      val notificationType = notification.`type` match {
          case NotificationType.BreakingNews => "breakingNews"
          case _                             => "other"
      }
      val end = Instant.now
      val processingTime = Duration.between(functionStartTime, end).toMillis
      val processingRate = numberOfTokens.toDouble / processingTime * 1000
      val start = Instant.ofEpochMilli(sentTime)
      val perfDimension = new Dimension().withName("platform").withValue(platform.map(_.toString).getOrElse("unknown"))
        .withName("type").withValue(notificationType)
      val perfMetrics: Seq[MetricDatum] = Seq(
        metricDatum("worker.notificationProcessingTime", StandardUnit.Milliseconds, Duration.between(start, end).toMillis.toDouble, perfDimension),
        metricDatum("worker.functionProcessingRate", StandardUnit.None, processingRate, perfDimension),
      )

      val metricsToEmit = notification.dryRun match {
        // case Some(true) => metrics
        case _          => metrics ++ perfMetrics
        }
      val req = new PutMetricDataRequest()
        .withNamespace(s"Notifications/$stage/workers")
        .withMetricData(metricsToEmit.asJava)
      cloudwatchClient.putMetricData(req)
      ()
    }
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
