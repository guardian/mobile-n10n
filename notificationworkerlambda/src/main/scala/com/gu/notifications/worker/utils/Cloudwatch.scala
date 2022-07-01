package com.gu.notifications.worker.utils

import cats.effect.IO
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}
import com.amazonaws.services.cloudwatch.{AmazonCloudWatch, AmazonCloudWatchClientBuilder}
import com.gu.notifications.worker.models.SendingResults
import fs2.Pipe
import models.Platform

import java.util.UUID
import scala.jdk.CollectionConverters._

trait Cloudwatch {
  def sendMetrics(stage: String, platform: Option[Platform], notificationId: UUID): Pipe[IO, SendingResults, Unit]
  def sendFailures(stage: String, platform: Platform): Pipe[IO, Throwable, Unit]
}

class CloudwatchImpl extends Cloudwatch {

  lazy val cloudwatchClient: AmazonCloudWatch = AmazonCloudWatchClientBuilder
    .standard()
    .withCredentials(Aws.credentialsProvider)
    .withRegion(Regions.EU_WEST_1)
    .build

  private def countDatum(name: String, value: Int, dimension: Dimension, notifDim: Dimension) =
    new MetricDatum()
      .withMetricName(name)
      .withUnit(StandardUnit.None)
      .withValue(value.toDouble)
      .withDimensions(dimension, notifDim)

  def sendMetrics(stage: String, platform: Option[Platform], notificationId: UUID): Pipe[IO, SendingResults, Unit] = _.evalMap { results =>
    IO.delay {
      val dimension = new Dimension().withName("platform").withValue(platform.map(_.toString).getOrElse("unknown"))
      val notifDimension = new Dimension().withName("notificationId").withValue(notificationId.toString)
      val metrics: Seq[MetricDatum] = Seq(
        countDatum("success", results.successCount, dimension, notifDimension),
        countDatum("failure", results.failureCount, dimension, notifDimension),
        countDatum("dryrun", results.dryRunCount, dimension, notifDimension),
        countDatum("total", results.total, dimension, notifDimension)
      )
      val req = new PutMetricDataRequest()
        .withNamespace(s"Notifications/$stage/workers")
        .withMetricData(metrics.asJava)
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
              .withValue(platform.toString),
            new Dimension()
              .withName("notificationId")
              .withValue(platform.toString),
          )).asJava))
      ()
    }
  }
}
