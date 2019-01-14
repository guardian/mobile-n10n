package com.gu.notifications.events

import java.util.concurrent.{Executors, ScheduledExecutorService}

import com.amazonaws.regions.Regions
import com.amazonaws.services.athena.{AmazonAthenaAsync, AmazonAthenaAsyncClient}
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClientBuilder}
import com.gu.notifications.events.aws.AwsClient.credentials


class AthenaLambda {
  val athenaMetrics = new AthenaMetrics()

  def handleRequest(): Unit = {
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
    val amazonAthenaAsync = AmazonAthenaAsyncClient.asyncBuilder()
      .withCredentials(credentials)
      .withRegion(Regions.EU_WEST_1)
      .build()
    try {
      function(amazonAthenaAsync)
    }
    finally {
      amazonAthenaAsync.shutdown()
    }
  }

  def withDynamoDb(function: AmazonDynamoDBAsync => Any): Unit = {
    val dynamoDBAsync = AmazonDynamoDBAsyncClientBuilder.standard()
      .withCredentials(credentials)
      .withRegion(Regions.EU_WEST_1)
      .build()
    try {
      function(dynamoDBAsync)
    }
    finally {
      dynamoDBAsync.shutdown()
    }
  }
}