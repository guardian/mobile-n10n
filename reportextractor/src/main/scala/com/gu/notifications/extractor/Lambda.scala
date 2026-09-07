package com.gu.notifications.extractor

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import aws.AsyncDynamo.{keyBetween, keyEquals}
import aws.DynamoJsonConversions
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue, QueryRequest}
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{ObjectCannedACL, PutObjectRequest}
import com.gu.{AppIdentity, AwsIdentity, DevIdentity}
import models.NotificationType
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsValue, Json}
import utils.MobileAwsCredentialsProvider

import scala.annotation.tailrec
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters._


class DateRange(
  @BeanProperty var from: String,
  @BeanProperty var to: String
) {
  def this() = {
    this(null, null)
  }
}

class Lambda extends RequestHandler[DateRange, Unit] {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val defaultAppName = "report-extractor"

  val identity: AppIdentity = 
   Option(System.getenv("MOBILE_LOCAL_DEV")) match {
      case Some(_) => DevIdentity(defaultAppName)
      case None =>
        AppIdentity
          .whoAmI(defaultAppName, MobileAwsCredentialsProvider.mobileAwsCredentialsProviderv2)
          .getOrElse(DevIdentity(defaultAppName))
    } 

  val region: Region = identity match {
    case AwsIdentity(_, _, _, region) => Region.of(region)
    case _ => Region.EU_WEST_1
  }

  val dynamoDB = DynamoDbClient.builder()
    .credentialsProvider(MobileAwsCredentialsProvider.mobileAwsCredentialsProviderv2)
    .region(region)
    .build()

  val s3 = S3Client.builder()
    .credentialsProvider(MobileAwsCredentialsProvider.mobileAwsCredentialsProviderv2)
    .region(region)
    .build()

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
    NotificationType.FootballMatchStatus,
    NotificationType.Editions
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
    val buffer: String = results.map(Json.stringify).mkString("\n")
    val putObjectRequest = PutObjectRequest.builder()
      .bucket("ophan-raw-push-notification")
      .key(s"$s3Path/date=$day/notifications.json")
      .acl(ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL)
      .build()
    s3.putObject(putObjectRequest, RequestBody.fromBytes(buffer.getBytes))
    logger.info(s"Extracted $notificationCount notifications for $day")
  }

  private def extractNotifications(day: LocalDate, notificationType: NotificationType): List[JsValue] = {
    val sentTimeIndex = "sentTime-index"

    val from = day.atStartOfDay().toString
    val to = day.plusDays(1).atStartOfDay().toString

    val query = QueryRequest.builder()
      .tableName(tableName)
      .indexName(sentTimeIndex)
      .keyConditions(Map(
        "type" -> keyEquals(NotificationType.toRep(notificationType)),
        "sentTime" -> keyBetween(from, to)
      ).asJava)
      .filterExpression("attribute_not_exists(notification.dryRun)")
      .build()

    @tailrec
    def recursiveFetch(startKey: Option[java.util.Map[String, AttributeValue]], agg: List[Map[String, AttributeValue]]): List[Map[String, AttributeValue]] = {
      val queryWithStartKey = startKey.fold(query)(key => query.toBuilder.exclusiveStartKey(key).build())

      val results = dynamoDB.query(queryWithStartKey)
      val items = results.items().asScala.toList.map(item => item.asScala.toMap)

      val lastKey = if (results.hasLastEvaluatedKey && !results.lastEvaluatedKey().isEmpty) Some(results.lastEvaluatedKey()) else None

      lastKey match {
        case Some(key) => recursiveFetch(Some(key), items)
        case None => items
      }
    }


    val results = recursiveFetch(None, Nil)

    results.map(DynamoJsonConversions.jsonFromAttributeMap)
  }
}
