package com.gu.notifications.workerlambda.utils

import cats.effect.IO
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import com.gu.notifications.workerlambda.models.{Platform, SendingResults}
import fs2.Pipe

import scala.jdk.CollectionConverters._

trait Cloudwatch {
  def sendMetrics(stage: String, platform: Option[Platform]): Pipe[IO, SendingResults, Unit]
  def sendFailures(stage: String, platform: Platform): Pipe[IO, Throwable, Unit]
}

class CloudwatchImpl extends Cloudwatch {

  lazy val cloudwatchClient: CloudWatchClient = CloudWatchClient
    .builder()
    .credentialsProvider(Aws.CredentialsProvider)
    .region(Region.EU_WEST_1)
    .build

  private def countDatum(name: String, value: Int, dimension: Dimension) =
    MetricDatum
      .builder
      .metricName(name)
      .unit(StandardUnit.NONE)
      .value(value.toDouble)
      .dimensions(dimension)
      .build

  def sendMetrics(stage: String, platform: Option[Platform]): Pipe[IO, SendingResults, Unit] = _.evalMap { results =>
    IO.delay {
      val dimension = Dimension.builder().name("platform").value(platform.map(_.toString).getOrElse("unknown")).build
      val metrics: Seq[MetricDatum] = Seq(
        countDatum("success", results.successCount, dimension),
        countDatum("failure", results.failureCount, dimension),
        countDatum("dryrun", results.dryRunCount, dimension),
        countDatum("total", results.total, dimension)
      )
      val req = PutMetricDataRequest
        .builder
        .namespace(s"Notifications/$stage/workers")
        .metricData(metrics.asJava)
        .build
      cloudwatchClient.putMetricData(req)
      ()
    }
  }

  def sendFailures(stage: String, platform: Platform): Pipe[IO, Throwable, Unit] = input => input.fold(0) {
    case (count, _) => count + 1
  }.evalMap { count =>
    IO.delay {
      cloudwatchClient.putMetricData(
        PutMetricDataRequest.builder
          .namespace(s"Notifications/$stage/harvester")
          .metricData(Seq(countDatum(
            "failure",
            count,
            Dimension.builder()
              .name("platform")
              .value(platform.toString).build)).asJava)
          .build)
      ()
    }
  }
}
