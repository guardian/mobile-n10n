package com.gu.notifications.events

import java.time.format.DateTimeFormatter
import java.time.{Duration, ZoneOffset, ZonedDateTime}
import java.util.concurrent.{ConcurrentLinkedQueue, Executors, TimeUnit}

import com.gu.notifications.events.AthenaLambda.athenaAsyncClient
import com.gu.notifications.events.dynamo.DynamoReportUpdater
import com.gu.notifications.events.model.{AggregationCounts, EventAggregation, NotificationReportEvent, PlatformCount}
import org.apache.logging.log4j.{LogManager, Logger}
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain, DefaultCredentialsProvider, ProfileCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.athena.AthenaAsyncClient
import software.amazon.awssdk.services.athena.model.{GetQueryExecutionRequest, GetQueryExecutionResponse, GetQueryResultsResponse, QueryExecutionContext, QueryExecutionState, ResultConfiguration, Row, StartQueryExecutionRequest, StartQueryExecutionResponse}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, Future, Promise, duration}

object AthenaLambda {
  val reportingWindow = Duration.ofHours(3)
  val athenaAsyncClient = new ScalaAthenaAsyncClient(AthenaAsyncClient.builder()
    .region(Region.EU_WEST_1)
    .credentialsProvider(AwsCredentialsProviderChain.builder()
      .addCredentialsProvider(ProfileCredentialsProvider.builder()
        .profileName("mobile")
        .build())
      .addCredentialsProvider(DefaultCredentialsProvider.builder().build())
      .build())
    .build())
}

case class Query(database: String, queryString: String, outputLocation: String)


class AthenaLambda {
  val scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

  import ExecutionContext.Implicits.global

  private val envDependencies = new EnvDependencies
  private val logger: Logger = LogManager.getLogger(classOf[AthenaLambda])
  private val stage: String = envDependencies.stage
  private val dynamoReportUpdater = new DynamoReportUpdater(stage)


  def route(query: Query, startOfReportingWindow: ZonedDateTime): Future[Unit] = {
    startQuery(query)
      .flatMap(fetchQueryResponse(_, rows => rows.map(cells => (cells.head, PlatformCount(cells(1).toInt, cells(2).toInt, cells(3).toInt))).groupBy(_._1).mapValues(_.map(_._2).head)))
      .flatMap(updateDynamoIfRecent(_, startOfReportingWindow))
      .map((aggregationCounts: AggregationCounts) => {
        logger.info(s"Aggregation counts $aggregationCounts")
        if (aggregationCounts.failure > 0) {
          throw new RuntimeException("Failures")
        }
      })
  }

  def startQuery(query: Query): Future[String] = {
    val startQueryAndGetId: Future[String] = executeQuery(query).map((startQueryExecutionResponse: StartQueryExecutionResponse) => startQueryExecutionResponse.queryExecutionId())
    startQueryAndGetId.flatMap((executionId: String) =>
      waitUntilQueryCompletes(executionId).map(_ => executionId)
    )
  }


  private def waitUntilQueryCompletes(id: String): Future[Unit] = {
    val promiseInASecond = Promise[Unit]
    val runnable: Runnable = () => promiseInASecond.success(())
    scheduledExecutorService.schedule(runnable, 1, TimeUnit.SECONDS)
    promiseInASecond.future.flatMap(_ => athenaAsyncClient.getQueryExecution(GetQueryExecutionRequest.builder()
      .queryExecutionId(id)
      .build()))
      .map((response: GetQueryExecutionResponse) => {
        val state = response.queryExecution().status().state()
        logger.info(s"Query $id state: $state")
        state match {
          case QueryExecutionState.QUEUED => waitUntilQueryCompletes(id)
          case QueryExecutionState.RUNNING => waitUntilQueryCompletes(id)
          case _ => Future.successful(())
        }
      }).flatten
  }


  private def updateDynamoIfRecent(notificationIdCounts: Map[String, PlatformCount], startOfReportingWindow: ZonedDateTime): Future[AggregationCounts] = {
    val notificationReportEvents = notificationIdCounts.toList.map { case (id, count) => NotificationReportEvent(id, EventAggregation(count)) }
    val resultCounts = dynamoReportUpdater.updateSetEventsReceivedAfter(notificationReportEvents, startOfReportingWindow)
    AggregationCounts.aggregateResultCounts(resultCounts)
  }

  private def fetchQueryResponse[T](id: String, transform: List[List[String]] => T): Future[T] = {
    val queue = new ConcurrentLinkedQueue[List[List[String]]]()
    athenaAsyncClient.subscribeToPagination(id, (getQueryResultsResponse: GetQueryResultsResponse) => {
      val rows: List[Row] = getQueryResultsResponse.resultSet.rows().asScala.toList
      logger.info(s"Headers: ${rows.head.data().asScala.toList.map(_.varCharValue())}")
      queue.add(rows.tail.map { row => row.data().asScala.toList.map(_.varCharValue()) })
    }).map(_ => transform(queue.asScala.flatten.toList))
  }

  private def executeQuery(query: Query): Future[StartQueryExecutionResponse] =
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
    val athenaOutputLocation = s"${envDependencies.athenaOutputLocation}/${now.toLocalDate.toString}/${now.getHour}"
    val athenaDatabase = envDependencies.athenaDatabase

    @tailrec
    def addPartitionFrom(fromTime: ZonedDateTime, started: List[Future[String]] = List()): List[Future[String]] = {
      if (fromTime.isAfter(now)) {
        started
      }
      else {
        val integerHour = fromTime.getHour
        val hour = if (integerHour < 10) s"0$integerHour" else integerHour.toString
        val date = toQueryDate(fromTime)
        val list = startQuery(Query(
          athenaDatabase,
          s"""ALTER TABLE raw_events_$stage
ADD IF NOT EXISTS PARTITION (date='$date', hour=$hour)
LOCATION '${envDependencies.ingestLocation}/date=$date/hour=$hour/'""".stripMargin, athenaOutputLocation)) :: started
        addPartitionFrom(fromTime.plusHours(1), list)
      }
    }

    val addPartitions = addPartitionFrom(startOfReportingWindow).reduce((a, b) => a.flatMap(_ => b))
    val fetchEventsQuery = Query(athenaDatabase,
      s"""SELECT notificationid,
         count(*) AS total,
         count_if(platform = 'ios') AS ios,
         count_if(platform = 'android') AS android
FROM notification_received_${stage.toLowerCase()}
WHERE partition_date = '${toQueryDate(startOfReportingWindow)}'
         AND partition_hour >= ${startOfReportingWindow.getHour}
GROUP BY  notificationid""".stripMargin, athenaOutputLocation)
    Await.result(
      addPartitions.flatMap(_ => route(fetchEventsQuery, startOfReportingWindow)), duration.Duration(4, TimeUnit.MINUTES))
  }

  private def toQueryDate(zonedDateTime: ZonedDateTime) = {
    zonedDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
  }
}
