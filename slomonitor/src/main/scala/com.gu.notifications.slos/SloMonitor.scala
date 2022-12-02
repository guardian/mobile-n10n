package com.gu.notifications.slos

import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.athena.AmazonAthenaAsyncClient
import com.amazonaws.services.cloudwatch.model.{MetricDatum, PutMetricDataRequest, StandardUnit}
import com.amazonaws.services.cloudwatch.{AmazonCloudWatch, AmazonCloudWatchClientBuilder}
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.athena.{Athena, Query}
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers.appendEntries
import org.slf4j.{Logger, LoggerFactory}

import java.time.{Instant, LocalDateTime, LocalTime, ZoneOffset}
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import scala.jdk.CollectionConverters._
import scala.concurrent.{Await, ExecutionContext, duration}
import scala.jdk.CollectionConverters.MapHasAsJava
import scala.util.{Failure, Success, Try}

object SloMonitor {

  import ExecutionContext.Implicits.global

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  implicit def mapToContext(c: Map[String, _]): LogstashMarker = appendEntries(c.asJava)

  val stage: String = System.getenv("STAGE")

  val credentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("mobile"),
    DefaultAWSCredentialsProviderChain.getInstance
  )

  val cloudWatchClient: AmazonCloudWatch = {
    AmazonCloudWatchClientBuilder.standard().withRegion(Regions.EU_WEST_1).build
  }

  implicit val athenaClient = AmazonAthenaAsyncClient.asyncBuilder()
    .withCredentials(credentials)
    .withRegion(Regions.EU_WEST_1)
    .build()

  implicit val scheduledExecutorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

  def pushMetrics(deliveryTimings: List[String]): Unit = {

    def metricDatum(name: String, resultLocation: Int) = {
      val deliveryCount: Double = Try(deliveryTimings(resultLocation).toDouble).getOrElse(0)
      new MetricDatum()
        .withMetricName(name)
        .withUnit(StandardUnit.Count)
        .withValue(deliveryCount)
    }

    val metrics = List(
      metricDatum("lessThan30", 0),
      metricDatum("lessThan60", 1),
      metricDatum("lessThan90", 2),
      metricDatum("lessThan120", 3),
      metricDatum("lessThan150", 4),
      metricDatum("lessThan180", 5),
      metricDatum("lessThan210", 6),
      metricDatum("lessThan240", 7),
      metricDatum("lessThan270", 8),
      metricDatum("lessThan300", 9),
      metricDatum("totalDeliveries", 10),
    )

    val request = new PutMetricDataRequest()
      .withNamespace(s"Notifications/$stage/slomonitor")
      .withMetricData(metrics.asJava)

    Try(cloudWatchClient.putMetricData(request)) match {
      case Failure(exception) => logger.error(s"Failed to push metrics to CloudWatch due to $exception", exception)
      case Success(_) => logger.info("Successfully pushed metrics to CloudWatch")
    }

  }

  def generateQueryString(notificationId: String, sentTime: LocalDateTime): String = {
    val partitionDate = if (sentTime.toLocalTime.isAfter(LocalTime.of(23, 57)))
      s"(partition_date = '${sentTime.toLocalDate}' OR partition_date = '${sentTime.toLocalDate.plusDays(1)}')"
    else
      s"partition_date = '${sentTime.toLocalDate}'"

    s"""
       |SELECT
       |  SUM(CASE
       |    WHEN DATE_DIFF('second', from_iso8601_timestamp('$sentTime'), received_timestamp) < 30 THEN 1
       |    ELSE 0
       |  END) AS less_than_30,
       |  SUM(CASE
       |    WHEN DATE_DIFF('second', from_iso8601_timestamp('$sentTime'), received_timestamp) < 60 THEN 1
       |    ELSE 0
       |  END) AS less_than_60,
       |  SUM(CASE
       |    WHEN DATE_DIFF('second', from_iso8601_timestamp('$sentTime'), received_timestamp) < 90 THEN 1
       |    ELSE 0
       |  END) AS less_than_90,
       |  SUM(CASE
       |    WHEN DATE_DIFF('second', from_iso8601_timestamp('$sentTime'), received_timestamp) < 120 THEN 1
       |    ELSE 0
       |  END) AS less_than_120,
       |  SUM(CASE
       |    WHEN DATE_DIFF('second', from_iso8601_timestamp('$sentTime'), received_timestamp) < 150 THEN 1
       |    ELSE 0
       |  END) AS less_than_150,
       |  SUM(CASE
       |    WHEN DATE_DIFF('second', from_iso8601_timestamp('$sentTime'), received_timestamp) < 180 THEN 1
       |    ELSE 0
       |  END) AS less_than_180,
       |  SUM(CASE
       |    WHEN DATE_DIFF('second', from_iso8601_timestamp('$sentTime'), received_timestamp) < 210 THEN 1
       |    ELSE 0
       |  END) AS less_than_210,
       |  SUM(CASE
       |    WHEN DATE_DIFF('second', from_iso8601_timestamp('$sentTime'), received_timestamp) < 240 THEN 1
       |    ELSE 0
       |  END) AS less_than_240,
       |  SUM(CASE
       |    WHEN DATE_DIFF('second', from_iso8601_timestamp('$sentTime'), received_timestamp) < 270 THEN 1
       |    ELSE 0
       |  END) AS less_than_270,
       |  SUM(CASE
       |    WHEN DATE_DIFF('second', from_iso8601_timestamp('$sentTime'), received_timestamp) < 300 THEN 1
       |    ELSE 0
       |  END) AS less_than_300,
       |  COUNT(*) AS total_deliveries
       |FROM notification_received_${stage.toLowerCase()}
       |WHERE notificationid = '$notificationId'
       |AND $partitionDate
     """.stripMargin
  }

  def handleMessage(event: SQSEvent): Unit = {
    logger.info(s"Running SLO monitor")
    val record = event.getRecords.get(0) // Batch size is defined as 1 in cdk
    val notificationId = record.getBody
    val sentTime: LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.getAttributes.get("SentTimestamp").toLong), ZoneOffset.UTC)
    val query = generateQueryString(notificationId, sentTime)
    logger.info(s"Starting query: $query")
    val result = Athena.startQuery(Query("notifications", query, s"s3://aws-mobile-event-logs-${this.stage.toLowerCase()}/athena/slo-monitoring"))
      .flatMap(Athena.fetchQueryResponse(_, rows => rows.head)
        .map(deliveryTimings => {
          pushMetrics(deliveryTimings)
          val deliveriesWithTwoMinutes = deliveryTimings(3)
          logger.info(Map(
            "notificationId" -> notificationId,
            "deliveriesWithin2mins" -> deliveriesWithTwoMinutes
          ),
            s"Notifications delivered within 120 seconds was $deliveriesWithTwoMinutes")
        })
      )
    Await.result(result, duration.Duration(4, TimeUnit.MINUTES))
  }

}