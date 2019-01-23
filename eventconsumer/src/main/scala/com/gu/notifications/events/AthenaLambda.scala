package com.gu.notifications.events

import java.util.concurrent.{Executors, ScheduledExecutorService}

import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.athena.{AmazonAthenaAsync, AmazonAthenaAsyncClient}
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClientBuilder}


class AthenaLambda {
  val athenaMetrics = new AthenaMetrics()
  lazy val credentials: AWSCredentialsProviderChain = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("mobile"),
    DefaultAWSCredentialsProviderChain.getInstance)
  lazy val scheduledExecutorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  lazy val amazonAthenaAsync: AmazonAthenaAsync = createAmazonAthenaAsync()
  lazy val amazonDynamoDBAsync: AmazonDynamoDBAsync = createAmazonDynamoDBAsync()

  def handleRequest(): Unit = {
    athenaMetrics.handleRequest()(amazonAthenaAsync, scheduledExecutorService, amazonDynamoDBAsync)
  }
  def handleRequestLocally(): Unit = {
    withScheduledExecutorService(implicit scheduledExecutorService =>
      withAthena(implicit athenaAsync =>
        withDynamoDb(implicit dynamoDbAsync =>
          athenaMetrics.handleRequest())))
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