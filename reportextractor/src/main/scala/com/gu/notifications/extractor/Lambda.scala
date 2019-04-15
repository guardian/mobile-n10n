package com.gu.notifications.extractor

import java.time.LocalDate

import aws.AsyncDynamo.{keyBetween, keyEquals}
import aws.DynamoJsonConversions
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBClientBuilder}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.gu.{AppIdentity, AwsIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import com.typesafe.config.Config
import models.NotificationType
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsValue, Json}
import utils.MobileAwsCredentialsProvider

import scala.annotation.tailrec
import scala.collection.JavaConverters._

case class DateRange(
  from: Option[LocalDate],
  to: Option[LocalDate]
)

class Lambda extends RequestHandler[DateRange, Unit] {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val credentials = new MobileAwsCredentialsProvider()

  val identity: AppIdentity = AppIdentity.whoAmI(defaultAppName = "report-extractor", credentials = credentials)

  val conf: Config = ConfigurationLoader.load(identity, credentials = credentials) {
    case AwsIdentity(_, _, stage, _) => SSMConfigurationLocation(s"/notifications/$stage/extractor")
  }

  def tableName: String = identity match {
    case AwsIdentity(_, _, stage, _) => s"mobile-notifications-reports-$stage"
    case _ => s"mobile-notifications-reports-CODE"
  }

  def region: Regions = identity match {
    case AwsIdentity(_, _, _, region) => Regions.fromName(region)
    case _ => Regions.EU_WEST_1
  }

  val notificationTypesToExtract: List[NotificationType] = List(
    NotificationType.BreakingNews,
    NotificationType.Content,
    NotificationType.FootballMatchStatus
  )

  override def handleRequest(input: DateRange, context: Context): Unit = {
    val dynamoDB = AmazonDynamoDBClientBuilder.standard().withCredentials(credentials).withRegion(region).build()
    val s3 = AmazonS3ClientBuilder.standard().withCredentials(credentials).withRegion(region).build()

    val results: List[JsValue] = notificationTypesToExtract.flatMap(nt => fetchNotifications(nt, input, dynamoDB))
    val buffer: String = results.map(Json.stringify).mkString

    s3.putObject("ophan-raw-push-notification", s"code-data/${LocalDate.now()}.json", buffer)
  }

  private def fetchNotifications(notificationType: NotificationType, input: DateRange, dynamoDB: AmazonDynamoDB): List[JsValue] = {
    val sentTimeIndex = "sentTime-index"
    val from = input.from.getOrElse(LocalDate.now().minusDays(1)).atStartOfDay().toString
    val to = input.to.getOrElse(LocalDate.now()).atStartOfDay().toString

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
