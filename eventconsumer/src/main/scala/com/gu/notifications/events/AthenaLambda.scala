package com.gu.notifications.events

import java.time.{Duration, ZoneOffset, ZonedDateTime}
import java.util.concurrent.{CompletableFuture, CompletionStage, ConcurrentLinkedQueue}

import com.gu.notifications.events.AthenaLambda.athenaAsyncClient
import com.gu.notifications.events.dynamo.DynamoReportUpdater
import com.gu.notifications.events.model.{AggregationCounts, EventAggregation, NotificationReportEvent, PlatformCount}
import org.apache.logging.log4j.{LogManager, Logger}
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain, DefaultCredentialsProvider, ProfileCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.athena.AthenaAsyncClient
import software.amazon.awssdk.services.athena.model.{GetQueryExecutionRequest, GetQueryExecutionResponse, GetQueryResultsRequest, GetQueryResultsResponse, QueryExecutionContext, QueryExecutionState, ResultConfiguration, Row, StartQueryExecutionRequest, StartQueryExecutionResponse}

import scala.annotation.tailrec
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
      .thenComposeAsync(fetchQueryResponse(_, rows => rows.map(cells => (cells.head, PlatformCount(cells(1).toInt, cells(2).toInt, cells(3).toInt))).groupBy(_._1).mapValues(_.map(_._2).head)))
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
    Thread.sleep(10000)
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

  private def fetchQueryResponse[T](id: String, transform: List[List[String]] => T): CompletableFuture[T] = {
    val queue = new ConcurrentLinkedQueue[List[List[String]]]()
    athenaAsyncClient.getQueryResultsPaginator(GetQueryResultsRequest.builder()
      .queryExecutionId(id)
      .build()).subscribe((getQueryResultsResponse: GetQueryResultsResponse) => {
      val rows: List[Row] = getQueryResultsResponse.resultSet.rows().asScala.toList
      logger.info(s"Headers: ${rows.head.data().asScala.toList.map(_.varCharValue())}")
      queue.add(rows.tail.map { row => row.data().asScala.toList.map(_.varCharValue()) })
    }).thenApplyAsync((_: Void) =>
      transform(queue.asScala.flatten.toList)
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
    val now = ZonedDateTime.now(ZoneOffset.UTC)
    val startOfReportingWindow: ZonedDateTime = now.minus(AthenaLambda.reportingWindow)
    @tailrec
    def addPartitionFrom(fromTime: ZonedDateTime, started: List[CompletableFuture[String]] = List()):List[CompletableFuture[String]] = {
      if(fromTime.isAfter(now)) {
        started
      }
      else {
        val hour = fromTime.getHour
        val date = toQueryDate(fromTime)
        val list = startQuery(Query(
          envDependencies.athenaDatabase,
          s"""ALTER TABLE raw_events_${envDependencies.stage}
ADD IF NOT EXISTS PARTITION (date='$date', hour=$hour)
LOCATION '${envDependencies.ingestLocation}/date=$date/hour=$hour/'""".stripMargin, envDependencies.athenaOutputLocation)) :: started
        addPartitionFrom(fromTime.plusHours(1), list)
      }
    }

    val addPartitions = addPartitionFrom(startOfReportingWindow).reduce((a,b) => a.thenComposeAsync(_ => b))
    val fetchEventsQuery = Query(envDependencies.athenaDatabase,
      s"""SELECT notificationid,
         count(*) AS total,
         count_if(platform = 'ios') AS ios,
         count_if(platform = 'android') AS android
FROM notification_received_${envDependencies.stage.toLowerCase()}
WHERE partition_date = '${toQueryDate(startOfReportingWindow)}'
         AND partition_hour >= ${startOfReportingWindow.getHour}
GROUP BY  notificationid""".stripMargin, envDependencies.athenaOutputLocation)
    addPartitions.thenComposeAsync(_ => route(fetchEventsQuery, startOfReportingWindow)).join()
  }

  private def toQueryDate(startOfReportingWindow: ZonedDateTime) = {
    s"""${startOfReportingWindow.getYear}-${startOfReportingWindow.getMonthValue}-${startOfReportingWindow.getDayOfMonth}"""
  }
}
