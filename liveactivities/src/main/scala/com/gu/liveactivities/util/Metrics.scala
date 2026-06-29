package com.gu.liveactivities.util

import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain, DefaultCredentialsProvider, ProfileCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}

import scala.util.{Failure, Success, Try}

/**
 * Publishes custom CloudWatch metrics for the Live Activities lambdas to the `Notifications/<stage>/liveactivities` namespace
 */
class Metrics(stage: String, lambdaName: String, cloudWatchClient: CloudWatchClient = Metrics.buildCloudWatchClient()) extends Logging {

  private val namespace = s"Notifications/$stage/liveactivities-$lambdaName"

  def increment(metric: Metrics.Metric): Unit = {
    val datumBuilder = MetricDatum.builder()
      .metricName(metric.name)
      .unit(StandardUnit.COUNT)
      .value(1.0)

    if (metric.dimensions.nonEmpty) {
      datumBuilder.dimensions(
        metric.dimensions.map { case (name, value) => Dimension.builder().name(name).value(value).build() }.toSeq: _*
      )
    }

    val request = PutMetricDataRequest.builder()
      .namespace(namespace)
      .metricData(datumBuilder.build())
      .build()

    Try(cloudWatchClient.putMetricData(request)) match {
      case Success(_) => ()
      case Failure(exception) =>
        logger.error(s"Failed to publish metric '${metric.name}' to CloudWatch namespace '$namespace': ${exception.getMessage}", exception)
    }
  }

  def recordApnsSuccess(): Unit =
    increment(Metrics.ApnsSuccess)

  def recordApnsNetworkError(): Unit =
    increment(Metrics.ApnsNetworkError)

  def recordApnsErrorResponse(statusCode: Int): Unit = {
    if (statusCode >= 400 && statusCode <= 499) increment(Metrics.Apns4xx)
    else if (statusCode >= 500 && statusCode <= 599) increment(Metrics.Apns5xx)
  }
}

object Metrics {
  // Lambda names used to scope the CloudWatch namespace to a specific lambda
  val BroadcastLambdaName = "broadcast"
  val ChannelManagerLambdaName = "channel-manager"
  val ChannelCleanUpLambdaName = "channel-cleanup"


  sealed abstract class Metric(val name: String, val dimensions: Map[String, String] = Map.empty)


  private val ReasonDimension = "Reason"
  sealed abstract class BroadcastNotProcessed(reason: String) extends Metric("BroadcastNotProcessed", Map(ReasonDimension -> reason))
  case object BroadcastNotAllowed extends BroadcastNotProcessed("BroadcastNotAllowed")
  case object ChannelNotActive    extends BroadcastNotProcessed("ChannelNotActive")
  case object DuplicateEvent      extends BroadcastNotProcessed("DuplicateEvent")
  case object OutOfOrderEvent     extends BroadcastNotProcessed("OutOfOrderEvent")

  private val OutcomeDimension = "Outcome"
  sealed abstract class ApnsResponse(outcome: String) extends Metric("ApnsResponse", Map(OutcomeDimension -> outcome))
  case object ApnsSuccess      extends ApnsResponse("2xx")
  case object Apns4xx          extends ApnsResponse("4xx")
  case object Apns5xx          extends ApnsResponse("5xx")
  case object ApnsNetworkError extends ApnsResponse("NetworkError")



  def buildCloudWatchClient(): CloudWatchClient =
    CloudWatchClient.builder()
      .region(Region.EU_WEST_1)
      .credentialsProvider(
        AwsCredentialsProviderChain.of(
          ProfileCredentialsProvider.builder.profileName("mobile").build,
          DefaultCredentialsProvider.builder.build(),
        )
      )
      .build()
}
