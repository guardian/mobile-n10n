package com.gu.notifications.worker.utils

import cats.effect.IO
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}
import com.amazonaws.services.cloudwatch.{AmazonCloudWatch, AmazonCloudWatchClientBuilder}
import com.gu.notifications.worker.models.SendingResults
import fs2.Sink
import models.Platform
import scala.collection.JavaConverters._

trait Cloudwatch {
  def sendMetrics(stage: String, platform: Platform): Sink[IO, SendingResults]
  def sendFailures(stage: String, platform: Platform): Sink[IO, Throwable]
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

  def sendMetrics(stage: String, platform: Platform): Sink[IO, SendingResults] = _.evalMap { results =>
    IO.delay {
      val dimension = new Dimension().withName("platform").withValue(platform.toString)
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

  def sendFailures(stage: String, platform: Platform): Sink[IO, Throwable] = input => input.fold(0) {
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
