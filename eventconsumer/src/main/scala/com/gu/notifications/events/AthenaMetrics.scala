package com.gu.notifications.events

import java.time.format.DateTimeFormatter
import java.time.{Duration, ZonedDateTime}
import java.util.concurrent.{ScheduledExecutorService, TimeUnit}

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.athena.AmazonAthenaAsync
import com.amazonaws.services.athena.model.{GetQueryExecutionRequest, GetQueryExecutionResult, GetQueryResultsRequest, GetQueryResultsResult, QueryExecutionContext, QueryExecutionState, ResultConfiguration, StartQueryExecutionRequest, StartQueryExecutionResult}
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.gu.notifications.events.dynamo.DynamoReportUpdater
import com.gu.notifications.events.model.{AggregationCounts, EventAggregation, NotificationReportEvent, PlatformCount}
import org.slf4j.{Logger, LoggerFactory}

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.concurrent.{Await, ExecutionContext, Future, Promise, duration}

object AthenaMetrics {
  val queuedString: String = QueryExecutionState.QUEUED.toString
  val runningString: String = QueryExecutionState.RUNNING.toString
  val reportingWindow: Duration = Duration.ofHours(3)
}

case class Query(database: String, queryString: String, outputLocation: String)


class AthenaMetrics {

  import ExecutionContext.Implicits.global

  private val envDependencies = new EnvDependencies
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  private val stage: String = envDependencies.stage

  val dynamoReportUpdater = new DynamoReportUpdater(stage)

  private def asyncHandle[REQ <: AmazonWebServiceRequest, RES](asyncHandlerConsumer: AsyncHandler[REQ, RES] => Any): Future[RES] = {
    val promise = Promise[RES]
    asyncHandlerConsumer(new AsyncHandler[REQ, RES] {
      override def onError(exception: Exception): Unit = promise.failure(exception)

      override def onSuccess(request: REQ, result: RES): Unit = promise.success(result)
    })
    promise.future
  }

  private def toQueryDate(zonedDateTime: ZonedDateTime): String = {
    zonedDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
  }

  private def startQuery(query: Query)(implicit athenaAsyncClient: AmazonAthenaAsync, scheduledExecutorService: ScheduledExecutorService): Future[String] = {
    def executeQuery(query: Query): Future[StartQueryExecutionResult] = asyncHandle[StartQueryExecutionRequest, StartQueryExecutionResult](asyncHandler =>
      athenaAsyncClient.startQueryExecutionAsync(new StartQueryExecutionRequest()
        .withQueryString(query.queryString)
        .withQueryExecutionContext(new QueryExecutionContext().withDatabase(query.database))
        .withResultConfiguration(new ResultConfiguration().withOutputLocation(query.outputLocation)), asyncHandler))

    def waitUntilQueryCompletes(id: String): Future[Unit] = {
      val promiseInASecond = Promise[Unit]
      val runnable: Runnable = () => promiseInASecond.success(())
      scheduledExecutorService.schedule(runnable, 1, TimeUnit.SECONDS)
      promiseInASecond.future.flatMap { _ =>
        asyncHandle[GetQueryExecutionRequest, GetQueryExecutionResult](asyncHandler =>
          athenaAsyncClient.getQueryExecutionAsync(new GetQueryExecutionRequest().withQueryExecutionId(id), asyncHandler))
      }.map((response: GetQueryExecutionResult) => {
        val state = response.getQueryExecution.getStatus.getState
        logger.info(s"Query $id state: $state")
        state match {
          case AthenaMetrics.queuedString => waitUntilQueryCompletes(id)
          case AthenaMetrics.runningString => waitUntilQueryCompletes(id)
          case _ => Future.successful(())
        }
      }).flatten
    }

    val startQueryAndGetId: Future[String] = executeQuery(query).map((startQueryExecutionResponse: StartQueryExecutionResult) => startQueryExecutionResponse.getQueryExecutionId)
    startQueryAndGetId.flatMap((executionId: String) => waitUntilQueryCompletes(executionId).map(_ => executionId))
  }

  private def updateDynamoIfRecent(notificationIdCounts: Map[String, PlatformCount], startOfReportingWindow: ZonedDateTime)(implicit dynamoDBAsyncClient: AmazonDynamoDBAsync): Future[AggregationCounts] = {
    val notificationReportEvents = notificationIdCounts.toList.map { case (id, count) => NotificationReportEvent(id, EventAggregation(count)) }
    val resultCounts = dynamoReportUpdater.updateSetEventsReceivedAfter(notificationReportEvents, startOfReportingWindow)
    AggregationCounts.aggregateResultCounts(resultCounts)
  }

  private def fetchQueryResponse[T](id: String, transform: List[List[String]] => T)(implicit athenaAsync: AmazonAthenaAsync): Future[T] = {
    def readAndProcessNext(getQueryResultsResult: GetQueryResultsResult, last: List[List[String]] = Nil): Future[List[List[String]]] = {
      val next = last ++ getQueryResultsResult.getResultSet.getRows.asScala.toList.map(row => row.getData.asScala.map(_.getVarCharValue).toList)
      Option(getQueryResultsResult.getNextToken) match {
        case Some(token) => asyncHandle[GetQueryResultsRequest, GetQueryResultsResult](asyncHandler =>
          athenaAsync.getQueryResultsAsync(new GetQueryResultsRequest().withQueryExecutionId(id).withNextToken(token), asyncHandler)).flatMap(readAndProcessNext(_, next))
        case None => Future.successful(next)
      }
    }

    asyncHandle[GetQueryResultsRequest, GetQueryResultsResult](asyncHandler => athenaAsync.getQueryResultsAsync(new GetQueryResultsRequest().withQueryExecutionId(id), asyncHandler))
      .flatMap(readAndProcessNext(_)).map {
      case _ :: tail => tail
      case _ => Nil
    }.map(transform(_))
  }

  private def addS3PartitionsToAthenaIndex(
    reportingWindow: ReportingWindow,
    athenaDatabase: String,
    athenaOutputLocation: String
  )(implicit amazonAthenaAsync: AmazonAthenaAsync, scheduledExecutorService: ScheduledExecutorService
  ): Future[String] = {
    @tailrec
    def addPartitionFrom(fromTime: ZonedDateTime, started: List[Future[String]] = List()): List[Future[String]] = {
      if (fromTime.isAfter(reportingWindow.end)) {
        started
      }
      else {
        val integerHour = fromTime.getHour
        val hour = if (integerHour < 10) s"0$integerHour" else integerHour.toString
        val date = toQueryDate(fromTime)
        val request = s"""
             |ALTER TABLE raw_events_$stage
             |ADD IF NOT EXISTS PARTITION (date='$date', hour=$hour)
             |LOCATION '${envDependencies.ingestLocation}/date=$date/hour=$hour/'
           """.stripMargin
        val list = startQuery(Query(athenaDatabase, request, athenaOutputLocation)) :: started
        addPartitionFrom(fromTime.plusHours(1), list)
      }
    }

    if (reportingWindow.reIndex) {
      addPartitionFrom(reportingWindow.start).reduce((a, b) => a.flatMap(_ => b))
    } else {
      Future.successful("")
    }
  }

  private def routeFromQueryToUpdateDynamoDb(query: Query, startOfReportingWindow: ZonedDateTime)(implicit athenaAsync: AmazonAthenaAsync, dynamoDBAsync: AmazonDynamoDBAsync, scheduledExecutorService: ScheduledExecutorService): Future[Unit] = {
    startQuery(query)
      .flatMap(fetchQueryResponse(_, rows => rows.map(cells =>
        (cells.head, PlatformCount(cells(1).toInt, cells(2).toInt, cells(3).toInt, Some(cells(4).toInt), Some(cells(5).toInt)))
      ).groupBy(_._1).view.mapValues(_.map(_._2).head).toMap))
      .flatMap(updateDynamoIfRecent(_, startOfReportingWindow))
      .map((aggregationCounts: AggregationCounts) => {
        logger.info(s"Aggregation counts $aggregationCounts")
        if (aggregationCounts.failure > 0) {
          throw new RuntimeException("Failures")
        }
      })
  }

  def handleRequest(reportingWindow: ReportingWindow)
    (implicit athenaAsyncClient: AmazonAthenaAsync, scheduledExecutorService: ScheduledExecutorService, dynamoDBAsyncClient: AmazonDynamoDBAsync): Unit = {
    val athenaOutputLocation = s"${envDependencies.athenaOutputLocation}/${reportingWindow.end.toLocalDate.toString}/${reportingWindow.end.getHour}"
    val athenaDatabase = envDependencies.athenaDatabase

    logger.info(s"Processing the reporting window $reportingWindow")

    val request = s"""
      |SELECT
      | 	notificationid,
      | 	count(*) AS total,
      | 	count_if(platform = 'ios') AS ios,
      | 	count_if(platform = 'android') AS android,
      | 	count_if(platform = 'ios-edition') AS iosEdition,
      | 	count_if(platform = 'android-edition') AS androidEdition
      |FROM
      |	 notification_received_${stage.toLowerCase()}
      |WHERE
      | 	(('${toQueryDate(reportingWindow.start)}' != '${toQueryDate(reportingWindow.end)}'
      | 		AND partition_date = '${toQueryDate(reportingWindow.end)}'
      | 	) OR (
      | 		partition_date = '${toQueryDate(reportingWindow.start)}'
      | 		AND partition_hour >= ${reportingWindow.start.getHour}
      | 	)) AND (provider != 'comment' OR provider IS NULL)
      |  AND notificationid != 'unknown'
      |  AND notificationid IS NOT NULL
      |GROUP BY
      |	 notificationid""".stripMargin

    val fetchEventsQuery = Query(athenaDatabase, request, athenaOutputLocation)

    val result = addS3PartitionsToAthenaIndex(reportingWindow, athenaDatabase, athenaOutputLocation)
      .flatMap(_ => routeFromQueryToUpdateDynamoDb(fetchEventsQuery, reportingWindow.start))

    Await.result(result, duration.Duration(4, TimeUnit.MINUTES))
  }

}
