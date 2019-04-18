package com.gu.notifications.extractor

import java.io.{BufferedInputStream, ByteArrayInputStream}
import java.time.temporal.ChronoUnit
import java.time.LocalDate

import aws.AsyncDynamo.{keyBetween, keyEquals}
import aws.DynamoJsonConversions
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.{CannedAccessControlList, ObjectMetadata, PutObjectRequest}
import com.gu.{AppIdentity, AwsIdentity}
import models.NotificationType
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsValue, Json}
import utils.MobileAwsCredentialsProvider

import scala.annotation.tailrec
import scala.beans.BeanProperty
import scala.collection.JavaConverters._


class DateRange(
  @BeanProperty var from: String,
  @BeanProperty var to: String
) {
  def this() {
    this(null, null)
  }
}

class Lambda extends RequestHandler[DateRange, Unit] {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val credentials = new MobileAwsCredentialsProvider()

  val identity: AppIdentity = AppIdentity.whoAmI(defaultAppName = "report-extractor", credentials = credentials)

  val region: Regions = identity match {
    case AwsIdentity(_, _, _, region) => Regions.fromName(region)
    case _ => Regions.EU_WEST_1
  }

  val dynamoDB = AmazonDynamoDBClientBuilder.standard().withCredentials(credentials).withRegion(region).build()
  val s3 = AmazonS3ClientBuilder.standard().withCredentials(credentials).withRegion(region).build()

  val tableName: String = identity match {
    case AwsIdentity(_, _, stage, _) => s"mobile-notifications-reports-$stage"
    case _ => s"mobile-notifications-reports-CODE"
  }

  val s3Path: String = identity match {
    case AwsIdentity(_, _, "PROD", _) => "data"
    case _ => "code-data"
  }

  val notificationTypesToExtract: List[NotificationType] = List(
    NotificationType.BreakingNews,
    NotificationType.Content,
    NotificationType.FootballMatchStatus
  )

  override def handleRequest(dateRange: DateRange, context: Context): Unit = {
    daysToExtract(dateRange).foreach(extractNotificationsByDay)
  }

  private def daysToExtract(dateRange: DateRange): List[LocalDate] = {
    lazy val yesterday = LocalDate.now().minusDays(1)
    val from = Option(dateRange.from).map(LocalDate.parse).getOrElse(yesterday)
    val to = Option(dateRange.to).map(LocalDate.parse).getOrElse(yesterday)

    val numberOfDays = ChronoUnit.DAYS.between(from, to)

    val results = (0L to numberOfDays).map(from.plusDays).toList

    logger.info(s"Extracting ${numberOfDays + 1} day(s): $results")

    results
  }

  private def extractNotificationsByDay(day: LocalDate): Unit = {
    logger.info(s"Extracting $day...")
    val results = notificationTypesToExtract.flatMap(nt => extractNotifications(day, nt))
    val notificationCount = results.size
    val buffer: String = results.map(Json.stringify).mkString
    val inputStream = new ByteArrayInputStream(buffer.getBytes)
    val objectMetaData = new ObjectMetadata()
    objectMetaData.setContentLength(buffer.getBytes().size)
    val putObjectRequest = new PutObjectRequest(
      "ophan-raw-push-notification",
      s"$s3Path/date=$day/notifications.json",
      inputStream,
      objectMetaData
    )
    putObjectRequest.withCannedAcl(CannedAccessControlList.BucketOwnerFullControl)
    s3.putObject("ophan-raw-push-notification", s"$s3Path/date=$day/notifications.json", buffer)
    logger.info(s"Extracted $notificationCount notifications for $day")
  }

  private def extractNotifications(day: LocalDate, notificationType: NotificationType): List[JsValue] = {
    val sentTimeIndex = "sentTime-index"

    val from = day.atStartOfDay().toString
    val to = day.plusDays(1).atStartOfDay().toString

    val query = new QueryRequest(tableName)
      .withIndexName(sentTimeIndex)
      .withKeyConditions(Map(
        "type" -> keyEquals(NotificationType.toRep(notificationType)),
        "sentTime" -> keyBetween(from, to)
      ).asJava)
      .withFilterExpression("attribute_not_exists(notification.dryRun)")

    @tailrec
    def recursiveFetch(startKey: Option[java.util.Map[String, AttributeValue]], agg: List[Map[String, AttributeValue]]): List[Map[String, AttributeValue]] = {
      val queryWithStartKey = startKey.fold(query)(key => query.withExclusiveStartKey(key))

      val results = dynamoDB.query(queryWithStartKey)
      val items = results.getItems.asScala.toList.map(item => item.asScala.toMap)

      val lastKey = Option(results.getLastEvaluatedKey).filter(!_.isEmpty)

      lastKey match {
        case Some(key) => recursiveFetch(Some(key), items)
        case None => items
      }
    }


    val results = recursiveFetch(None, Nil)

    results.map(DynamoJsonConversions.jsonFromAttributeMap)
  }
}
