package com.gu.liveactivities.util

import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain, DefaultCredentialsProvider, ProfileCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.{MetricDatum, PutMetricDataRequest, StandardUnit}

import scala.util.{Failure, Success, Try}

/**
 * Publishes custom CloudWatch metrics for the Live Activities lambdas to the `Notifications/<stage>/liveactivities` namespace
 */
class Metrics(stage: String, lambdaName: String, cloudWatchClient: CloudWatchClient = Metrics.buildCloudWatchClient()) extends Logging {

  private val namespace = s"Notifications/$stage/liveactivities-$lambdaName"

  def increment(metricName: String): Unit = {
    val datum = MetricDatum.builder()
      .metricName(metricName)
      .unit(StandardUnit.COUNT)
      .value(1.0)
      .build()

    val request = PutMetricDataRequest.builder()
      .namespace(namespace)
      .metricData(datum)
      .build()

    Try(cloudWatchClient.putMetricData(request)) match {
      case Success(_) => ()
      case Failure(exception) =>
        logger.error(s"Failed to publish metric '$metricName' to CloudWatch namespace '$namespace': ${exception.getMessage}", exception)
    }
  }

  def recordApnsErrorResponse(statusCode: Int): Unit = {
    if (statusCode >= 400 && statusCode <= 499) increment(Metrics.APNS4xx)
    else if (statusCode >= 500 && statusCode <= 599) increment(Metrics.APNS5xx)
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
  val APNS200 = "APNS200"
  val APNS4xx = "APNS4xx"
  val APNS5xx = "APNS5xx"
  val APNSNetworkError = "APNSNetworkError"



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
