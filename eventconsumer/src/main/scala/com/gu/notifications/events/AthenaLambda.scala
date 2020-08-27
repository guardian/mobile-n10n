package com.gu.notifications.events

import java.time.ZonedDateTime
import java.util.concurrent.{Executors, ScheduledExecutorService}

import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.athena.{AmazonAthenaAsync, AmazonAthenaAsyncClient}
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClientBuilder}
import com.football.notifications.events.AthenaMetrics

import scala.beans.BeanProperty

// Using Java style here to interoperate with JVM Lambda runtime
class LambdaParameters{
  @BeanProperty var start: String = null
  @BeanProperty var end: String = null
  @BeanProperty var reindex: Boolean = false
}

case class ReportingWindow(start: ZonedDateTime, end: ZonedDateTime, reIndex: Boolean)

object ReportingWindow {
  def default = ReportingWindow(
    start = ZonedDateTime.now().minus(AthenaMetrics.reportingWindow),
    end = ZonedDateTime.now(),
    reIndex = true
  )
}

class AthenaLambda {
  val athenaMetrics = new AthenaMetrics()
  lazy val credentials: AWSCredentialsProviderChain = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("mobile"),
    DefaultAWSCredentialsProviderChain.getInstance)
  lazy val scheduledExecutorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  lazy val amazonAthenaAsync: AmazonAthenaAsync = createAmazonAthenaAsync()
  lazy val amazonDynamoDBAsync: AmazonDynamoDBAsync = createAmazonDynamoDBAsync()

  def handleRequest(lambdaParams: LambdaParameters): Unit = {
    val reportingWindowParam = for {
      p <- Option(lambdaParams)
      start <- Option(p.start).map(ZonedDateTime.parse)
      end <- Option(p.end).map(ZonedDateTime.parse)
      reIndex <- Option(p.reindex)
    } yield ReportingWindow(start, end, reIndex)

    val reportingWindow = reportingWindowParam.getOrElse(ReportingWindow.default)

    athenaMetrics.handleRequest(reportingWindow)(amazonAthenaAsync, scheduledExecutorService, amazonDynamoDBAsync)
  }
  def handleRequestLocally(): Unit = {
    withScheduledExecutorService(implicit scheduledExecutorService =>
      withAthena(implicit athenaAsync =>
        withDynamoDb(implicit dynamoDbAsync =>
          athenaMetrics.handleRequest(ReportingWindow.default))))
  }

  def withScheduledExecutorService(function: ScheduledExecutorService => Any): Unit = {
    val scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    try {
      function(scheduledExecutorService)
    }
    finally {
      scheduledExecutorService.shutdown()
    }
  }

  def withAthena(function: AmazonAthenaAsync => Any): Unit = {
    val amazonAthenaAsync = createAmazonAthenaAsync()
    try {
      function(amazonAthenaAsync)
    }
    finally {
      amazonAthenaAsync.shutdown()
    }
  }

  private def createAmazonAthenaAsync(): AmazonAthenaAsync = {
    AmazonAthenaAsyncClient.asyncBuilder()
      .withCredentials(credentials)
      .withRegion(Regions.EU_WEST_1)
      .build()
  }

  def withDynamoDb(function: AmazonDynamoDBAsync => Any): Unit = {
    val dynamoDBAsync = createAmazonDynamoDBAsync()
    try {
      function(dynamoDBAsync)
    }
    finally {
      dynamoDBAsync.shutdown()
    }
  }

  private def createAmazonDynamoDBAsync(): AmazonDynamoDBAsync = {
    AmazonDynamoDBAsyncClientBuilder.standard()
      .withCredentials(credentials)
      .withRegion(Regions.EU_WEST_1)
      .build()
  }
}