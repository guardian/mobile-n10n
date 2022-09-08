package com.gu.notifications.events

import com.amazonaws.services.athena.AmazonAthenaAsync
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.gu.notifications.athena.{Athena, Query}
import com.gu.notifications.events.dynamo.DynamoReportUpdater
import com.gu.notifications.events.model.{AggregationCounts, EventAggregation, NotificationReportEvent, PlatformCount}
import org.slf4j.{Logger, LoggerFactory}

import java.time.ZonedDateTime
import java.util.concurrent.{ScheduledExecutorService, TimeUnit}
import scala.annotation.tailrec
import scala.concurrent._

class AthenaMetrics {

  import ExecutionContext.Implicits.global

  private val envDependencies = new EnvDependencies
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  private val stage: String = envDependencies.stage
  val dynamoReportUpdater = new DynamoReportUpdater(stage)

  private def updateDynamoIfRecent(notificationIdCounts: Map[String, PlatformCount], startOfReportingWindow: ZonedDateTime)(implicit dynamoDBAsyncClient: AmazonDynamoDBAsync): Future[AggregationCounts] = {
    val notificationReportEvents = notificationIdCounts.toList.map { case (id, count) => NotificationReportEvent(id, EventAggregation(count)) }
    val resultCounts = dynamoReportUpdater.updateSetEventsReceivedAfter(notificationReportEvents, startOfReportingWindow)
    AggregationCounts.aggregateResultCounts(resultCounts)
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
        val date = Athena.toQueryDate(fromTime)
        val request = s"""
             |ALTER TABLE raw_events_$stage
             |ADD IF NOT EXISTS PARTITION (date='$date', hour=$hour)
             |LOCATION '${envDependencies.ingestLocation}/date=$date/hour=$hour/'
           """.stripMargin
        val list = Athena.startQuery(Query(athenaDatabase, request, athenaOutputLocation)) :: started
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
    Athena.startQuery(query)
      .flatMap(Athena.fetchQueryResponse(_, rows => rows.map(cells =>
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
      | 	(('${Athena.toQueryDate(reportingWindow.start)}' != '${Athena.toQueryDate(reportingWindow.end)}'
      | 		AND partition_date = '${Athena.toQueryDate(reportingWindow.end)}'
      | 	) OR (
      | 		partition_date = '${Athena.toQueryDate(reportingWindow.start)}'
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
