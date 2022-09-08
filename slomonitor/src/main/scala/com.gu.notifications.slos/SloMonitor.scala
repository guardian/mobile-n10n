package com.gu.notifications.slos

import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.athena.AmazonAthenaAsyncClient
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.athena.{Athena, Query}
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers.appendEntries
import org.slf4j.{Logger, LoggerFactory}

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, duration}
import scala.jdk.CollectionConverters.MapHasAsJava

object SloMonitor {

  import ExecutionContext.Implicits.global

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  implicit def mapToContext(c: Map[String, _]): LogstashMarker = appendEntries(c.asJava)

  val stage: String = System.getenv("STAGE")

  val credentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("mobile"),
    DefaultAWSCredentialsProviderChain.getInstance
  )

  implicit val athenaClient = AmazonAthenaAsyncClient.asyncBuilder()
    .withCredentials(credentials)
    .withRegion(Regions.EU_WEST_1)
    .build()

  implicit val scheduledExecutorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

  def calculatePercentage(actualDeliveries: Double, expectedDeliveries: Double): Double = {
    (actualDeliveries / expectedDeliveries) * 100
  }

  def handleMessage(event: SQSEvent): Unit = {
    logger.info(s"Running SLO monitor")
    val record = event.getRecords.get(0) // Batch size is defined as 1 in cdk
    val notificationId = record.getBody
    val sentTime: LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.getAttributes.get("SentTimestamp").toLong), ZoneOffset.UTC)
    // The number of expected deliveries is hardcoded for the purposes of this prototype, but we could:
    // a) Send this as part of the SQS message (from the notifications app)
    // b) Obtain it via the DynamoDB Report table
    // c) Something else...
    val expectedDeliveries: Int = 100 // FIXME
    val query =
      s"""
         |SELECT COUNT(*)
         |FROM notification_received_${stage.toLowerCase()}
         |WHERE notificationid = '${notificationId}'
         |AND partition_date = '${sentTime.toLocalDate}'
         |AND DATE_DIFF('second', from_iso8601_timestamp('${sentTime}'), received_timestamp) < 120
      """.stripMargin
    logger.info(s"Starting query: $query")
    val result = Athena.startQuery(Query("notifications", query, s"s3://aws-mobile-event-logs-${this.stage.toLowerCase()}/athena/slo-monitoring"))
      .flatMap(Athena.fetchQueryResponse(_, rows => rows.head.head)
        .map(actualDeliveries => {
          // val percentage = calculatePercentage(actualDeliveries.toDouble, expectedDeliveries.toDouble)
          // In the future we could log structured data (for visualisation in Kibana) & send this as a CloudWatch metric here

          // For the purposes of validating the approach we decided to log only the actual deliveries
          // We will compare the actual deliveries logged against the data we see in bigquery as a way to validate accuracy of the solution/setup
          logger.info(Map(
            "notificationId" -> notificationId,
            "deliveriesWithin2mins" -> actualDeliveries
          ),
          s"Notifications delivered within 120 seconds was $actualDeliveries")
        })
      )
    Await.result(result, duration.Duration(4, TimeUnit.MINUTES))
  }

}