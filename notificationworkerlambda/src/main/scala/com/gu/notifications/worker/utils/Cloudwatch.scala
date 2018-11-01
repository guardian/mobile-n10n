package com.gu.notifications.worker.utils

import cats.effect.IO
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClientBuilder
import com.amazonaws.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}
import com.gu.notifications.worker.models.SendingResults
import fs2.Pipe
import models.Platform

import scala.collection.JavaConverters._

object Cloudwatch {

  lazy val cloudwatchClient = AmazonCloudWatchAsyncClientBuilder.defaultClient()

  private def countDatum(name: String, value: Int, dimension: Dimension) =
    new MetricDatum()
      .withMetricName(name)
      .withUnit(StandardUnit.None)
      .withValue(value.toDouble)
      .withDimensions(dimension)

  def sendMetrics(stage: String, platform: Platform): Pipe[IO, SendingResults, Unit] =
    _.evalMap { results =>
      IO.delay {
        val dimension = new Dimension().withName("platform").withValue(platform.toString)
        val metrics: Seq[MetricDatum] = Seq(
          countDatum("success", results.successCount, dimension),
          countDatum("failure", results.failureCount, dimension),
          countDatum("total", results.successCount + results.failureCount, dimension)
        )
        val req = new PutMetricDataRequest()
          .withNamespace(s"Notifications/$stage/workers")
          .withMetricData(metrics.asJava)
        cloudwatchClient.putMetricDataAsync(req)
        ()
      }
    }
}
