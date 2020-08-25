
package com.gu.mobile.notifications.football

import java.net.URL

import com.amazonaws.regions.Regions._
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient
import com.gu.mobile.notifications.football.lib._
import com.gu.Logging
import com.gu.contentapi.client.{ContentApiClient, GuardianContentClient}
import com.gu.mobile.notifications.client.ApiClient
import com.gu.mobile.notifications.football.notificationbuilders.MatchStatusNotificationBuilder
import org.joda.time.{DateTime, DateTimeUtils}
import play.api.libs.json.Json

import scala.concurrent.{Await, TimeoutException}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationLong
import scala.io.Source
import scala.util.Failure

object Lambda extends Logging {

  var cachedLambda: Boolean = false

  def tableName = s"mobile-notifications-football-notifications-${configuration.stage}"

  lazy val configuration: Configuration = {
    logger.debug("Creating configuration")
    new Configuration()
  }

  lazy val paFootballClient: PaFootballClient = {
    logger.debug("Creating pa football client")
    new PaFootballClient(configuration.paApiKey, configuration.paHost)
  }

  lazy val dynamoDBClient: AmazonDynamoDBAsyncClient = {
    logger.debug("Creating dynamo db client")
    new AmazonDynamoDBAsyncClient(configuration.credentials).withRegion(EU_WEST_1)
  }

  lazy val capiClient: ContentApiClient = new GuardianContentClient(configuration.capiApiKey)

  lazy val syntheticMatchEventGenerator = new SyntheticMatchEventGenerator()

  lazy val notificationHttpProvider = new NotificationHttpProvider()

  lazy val notificationClient = ApiClient(
    host = configuration.notificationsHost,
    apiKey = configuration.notificationsApiKey,
    httpProvider = notificationHttpProvider
  )

  lazy val notificationSender = new NotificationSender(notificationClient)

  lazy val matchStatusNotificationBuilder = new MatchStatusNotificationBuilder(configuration.mapiHost)

  lazy val eventConsumer = new EventConsumer(matchStatusNotificationBuilder)

  lazy val distinctCheck = new DynamoDistinctCheck(dynamoDBClient, tableName)

  lazy val eventFilter = new EventFilter(distinctCheck)

  lazy val footballData = new FootballData(paFootballClient, syntheticMatchEventGenerator)

  lazy val articleSearcher = new ArticleSearcher(capiClient)

  def debugSetTime(): Unit = {
    // this is only used to debug
    if (configuration.stage == "CODE") {
      val is = new URL("https://hdjq4n85yi.execute-api.eu-west-1.amazonaws.com/Prod/getTime").openStream()
      val json = Json.parse(Source.fromInputStream(is).mkString)
      val date = DateTime.parse((json \ "currentDate").as[String])
      logger.info(s"Force the date to $date")
      DateTimeUtils.setCurrentMillisFixed(date.getMillis)
    }
  }

  private def logContainer() = {
    if (cachedLambda) {
      logger.info("Re-using existing container")
    } else {
      cachedLambda = true
      logger.info("Starting new container")
    }
  }

  def handler(): String = {
    debugSetTime()
    logContainer()

    val processing = footballData.pollFootballData
      .flatMap(articleSearcher.tryToMatchWithCapiArticle)
      .map(_.flatMap(eventConsumer.eventsToNotifications))
      .flatMap(eventFilter.filterNotifications)
      .flatMap(notificationSender.sendNotifications)

    // we're in a lambda so we do need to block the main thread until processing is finished
    val result = Await.ready(processing, 40.seconds)

    result.value match {
      case Some(Failure(e: TimeoutException)) => logger.error("Task timed out", e)
      case Some(Failure(e: Exception)) =>
        logger.error("Something went wrong", e)
        throw e
      case _ =>
    }

    logger.info("Finished processing")
    "done"
  }

  def main(args: Array[String]): Unit = {
    while (true) {
      handler()
      Thread.sleep(10000)
    }
  }

}
