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

  def increment(metricName: String): Unit =
    increment(metricName, Map.empty)

  def increment(metricName: String, dimensions: Map[String, String]): Unit = {
    val datumBuilder = MetricDatum.builder()
      .metricName(metricName)
      .unit(StandardUnit.COUNT)
      .value(1.0)

    if (dimensions.nonEmpty) {
      datumBuilder.dimensions(
        dimensions.map { case (name, value) => Dimension.builder().name(name).value(value).build() }.toSeq: _*
      )
    }

    val request = PutMetricDataRequest.builder()
      .namespace(namespace)
      .metricData(datumBuilder.build())
      .build()

    Try(cloudWatchClient.putMetricData(request)) match {
      case Success(_) => ()
      case Failure(exception) =>
        logger.error(s"Failed to publish metric '$metricName' to CloudWatch namespace '$namespace': ${exception.getMessage}", exception)
    }
  }

  def recordApnsSuccess(): Unit =
    increment(Metrics.ApnsResponse, Map(Metrics.OutcomeDimension -> Metrics.ApnsOutcome2xx))

  def recordApnsNetworkError(): Unit =
    increment(Metrics.ApnsResponse, Map(Metrics.OutcomeDimension -> Metrics.ApnsOutcomeNetworkError))

  def recordApnsErrorResponse(statusCode: Int): Unit = {
    if (statusCode >= 400 && statusCode <= 499) increment(Metrics.ApnsResponse, Map(Metrics.OutcomeDimension -> Metrics.ApnsOutcome4xx))
    else if (statusCode >= 500 && statusCode <= 599) increment(Metrics.ApnsResponse, Map(Metrics.OutcomeDimension -> Metrics.ApnsOutcome5xx))
  }
}

object Metrics {
  // Lambda names used to scope the CloudWatch namespace to a specific lambda
  val BroadcastLambdaName = "broadcast"
  val ChannelManagerLambdaName = "channel-manager"
  val ChannelCleanUpLambdaName = "channel-cleanup"

  val BroadcastProcessed = "BroadcastProcessed"
  val BroadcastNotProcessed = "BroadcastNotProcessed"
  val BroadcastNotAllowed = "BroadcastNotAllowed"
  val ChannelNotActive = "ChannelNotActive"
  val DuplicateEvent = "DuplicateEvent"
  val OutOfOrderEvent = "OutOfOrderEvent"

  // APNS responses are tracked as a single metric distinguished by an `Outcome` dimension
  val ApnsResponse = "ApnsResponse"
  val OutcomeDimension = "Outcome"
  val ApnsOutcome2xx = "2xx"
  val ApnsOutcome4xx = "4xx"
  val ApnsOutcome5xx = "5xx"
  val ApnsOutcomeNetworkError = "NetworkError"



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
