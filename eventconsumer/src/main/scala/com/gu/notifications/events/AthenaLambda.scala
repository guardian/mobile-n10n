package com.gu.notifications.events

import java.time.{Duration, ZonedDateTime}
import java.util.concurrent.{CompletableFuture, CompletionStage, ConcurrentLinkedQueue}

import com.gu.notifications.events.AthenaLambda.athenaAsyncClient
import com.gu.notifications.events.dynamo.DynamoReportUpdater
import com.gu.notifications.events.model.{AggregationCounts, EventAggregation, NotificationReportEvent, PlatformCount}
import org.apache.logging.log4j.{LogManager, Logger}
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain, DefaultCredentialsProvider, ProfileCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.athena.AthenaAsyncClient
import software.amazon.awssdk.services.athena.model.{GetQueryExecutionRequest, GetQueryExecutionResponse, GetQueryResultsRequest, GetQueryResultsResponse, QueryExecutionContext, QueryExecutionState, ResultConfiguration, StartQueryExecutionRequest, StartQueryExecutionResponse}

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters
import scala.concurrent.ExecutionContext

object AthenaLambda {
  val reportingWindow = Duration.ofHours(3)
  val athenaAsyncClient = AthenaAsyncClient.builder()
    .region(Region.EU_WEST_1)
    .credentialsProvider(AwsCredentialsProviderChain.builder()
      .addCredentialsProvider(ProfileCredentialsProvider.builder()
        .profileName("mobile")
        .build())
      .addCredentialsProvider(DefaultCredentialsProvider.builder().build())
      .build())
    .build()
}

case class Query(database: String, queryString: String, outputLocation: String)

class AthenaLambda {

  import ExecutionContext.Implicits.global

  private val envDependencies = new EnvDependencies
  private val logger: Logger = LogManager.getLogger(classOf[AthenaLambda])
  private val dynamoReportUpdater = new DynamoReportUpdater(envDependencies.stage)


  def route(query: Query, startOfReportingWindow: ZonedDateTime): CompletableFuture[Void] = {
    startQuery(query)
      .thenComposeAsync(fetchQueryResponse)
      .thenComposeAsync(updateDynamoIfRecent(_, startOfReportingWindow))
      .thenAcceptAsync((aggregationCounts: AggregationCounts) => {
        logger.info(s"Aggregation counts $aggregationCounts")
        if (aggregationCounts.failure > 0) {
          throw new RuntimeException("Failures")
        }
      })
  }

  def startQuery(query: Query): CompletableFuture[String] = {
    val startQueryAndGetId: CompletableFuture[String] = executeQuery(query).thenApplyAsync((startQueryExecutionResponse: StartQueryExecutionResponse) => startQueryExecutionResponse.queryExecutionId())
    startQueryAndGetId.thenComposeAsync((executionId: String) =>
      waitUntilQueryCompletes(executionId).thenApplyAsync((_: Void) => executionId)
    )
  }


  private def waitUntilQueryCompletes(id: String): CompletableFuture[Void] = {
    Thread.sleep(1000)
    athenaAsyncClient.getQueryExecution(GetQueryExecutionRequest.builder()
      .queryExecutionId(id)
      .build()).thenComposeAsync((response: GetQueryExecutionResponse) => {
      response.queryExecution().status().state() match {
        case QueryExecutionState.QUEUED => waitUntilQueryCompletes(id)
        case QueryExecutionState.RUNNING => waitUntilQueryCompletes(id)
        case _ => CompletableFuture.completedFuture(null)
      }
    })
  }


  private def updateDynamoIfRecent(notificationIdCounts: Map[String, PlatformCount], startOfReportingWindow: ZonedDateTime): CompletionStage[AggregationCounts] = {
    val notificationReportEvents = notificationIdCounts.toList.map { case (id, count) => NotificationReportEvent(id, EventAggregation(count)) }
    val resultCounts = dynamoReportUpdater.updateSetEventsReceivedAfter(notificationReportEvents, startOfReportingWindow)
    val aggregatedCounts = AggregationCounts.aggregateResultCounts(resultCounts)
    FutureConverters.toJava(aggregatedCounts)
  }

  private def fetchQueryResponse(id: String)(implicit executionContext: ExecutionContext): CompletableFuture[Map[String, PlatformCount]] = {
    val queue = new ConcurrentLinkedQueue[List[(String, PlatformCount)]]()
    athenaAsyncClient.getQueryResultsPaginator(GetQueryResultsRequest.builder()
      .queryExecutionId(id)
      .build()).subscribe((getQueryResultsResponse: GetQueryResultsResponse) => {
      queue.add(getQueryResultsResponse.resultSet.rows().asScala.toList.tail.map { row =>
        val cells: List[String] = row.data().asScala.toList.map(_.varCharValue())
        val count = cells(2).toInt
        (cells.head, cells(1) match {
          case "android" => PlatformCount(count, 0, count)
          case "ios" => PlatformCount(count, count, 0)
          case _ => PlatformCount(0, 0, 0)
        })
      })
    }).thenApplyAsync((_: Void) =>
      queue.asScala.flatten.toList.groupBy(_._1).mapValues(_.map(_._2).reduce(PlatformCount.combine))
    )
  }

  private def executeQuery(query: Query): CompletableFuture[StartQueryExecutionResponse] =
    athenaAsyncClient.startQueryExecution(StartQueryExecutionRequest.builder()
      .queryExecutionContext(QueryExecutionContext.builder()
        .database(query.database)
        .build())
      .queryString(query.queryString)
      .resultConfiguration(ResultConfiguration.builder()
        .outputLocation(query.outputLocation)
        .build())
      .build())

  def handleRequest(): Unit = {
    val startOfReportingWindow = ZonedDateTime.now().minus(AthenaLambda.reportingWindow)
    val query = Query(envDependencies.athenaTable,
      s"""SELECT notificationid,
         platform,
         count(*) as count
        FROM notification_received_code
WHERE partition_date = '${startOfReportingWindow.getYear}-${startOfReportingWindow.getMonthValue}-${startOfReportingWindow.getDayOfMonth}'
        AND partition_hour >= ${startOfReportingWindow.getHour}
GROUP BY  notificationid, platform, provider""".stripMargin, envDependencies.athenaOutputLocation)
    new AthenaLambda().route(query, startOfReportingWindow).join()
  }

}
