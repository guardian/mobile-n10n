package com.gu.mobile.notifications.football

import java.net.{URI, URL}
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClientBuilder}
import com.gu.contentapi.client.GuardianContentClient
import com.gu.mobile.notifications.football.lib.{ArticleSearcher, DynamoDistinctCheck, DynamoMatchLiveActivity, DynamoMatchNotification, EventConsumer, EventFilter, FootballData, LiveActivityEventConsumer, LiveActivityPusher, NotificationHttpProvider, NotificationSender, NotificationsApiClient, PaFootballClient, SyntheticMatchEventGenerator}
import com.gu.mobile.notifications.football.notificationbuilders.{MatchStatusLiveActivityPayloadBuilder, MatchStatusNotificationBuilder}
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, TimeoutException}
import scala.io.Source
import scala.util.Failure
import com.gu.mobile.notifications.client.models.NotificationPayload
import com.gu.mobile.notifications.client.models.liveActitivites.LiveActivityPayload
import com.gu.mobile.notifications.football.models.MatchDataWithArticle
import scala.concurrent.Future
import org.scanamo.generic.auto._

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

  lazy val dynamoDBClient: AmazonDynamoDBAsync = {
    logger.debug("Creating dynamo db client")
    AmazonDynamoDBAsyncClientBuilder.standard()
      .withCredentials(configuration.credentials)
      .withRegion(Regions.EU_WEST_1)
      .build()
  }

  lazy val capiClient = GuardianContentClient(configuration.capiApiKey)

  lazy val syntheticMatchEventGenerator = new SyntheticMatchEventGenerator()

  lazy val notificationHttpProvider = new NotificationHttpProvider()

  val apiClient = new NotificationsApiClient(configuration)

  lazy val footballData = new FootballData(paFootballClient, syntheticMatchEventGenerator)

  lazy val articleSearcher = new ArticleSearcher(capiClient)

  lazy val notificationHandler = new NotificationHandler(configuration, apiClient, dynamoDBClient, tableName)

  lazy val liveActivityHandler = new LiveActivityHandler(configuration, dynamoDBClient, tableName)

  // live activities //
  lazy val liveActivityPusher = new LiveActivityPusher()
  lazy val matchStatusLiveActivityPayloadBuilder = new MatchStatusLiveActivityPayloadBuilder(configuration.mapiHost)
  lazy val liveActivityEventConsumer = new LiveActivityEventConsumer(matchStatusLiveActivityPayloadBuilder)

  def getZonedDateTime(): ZonedDateTime = {
    val zonedDateTime = if (configuration.stage == "CODE") {
      val is = URI.create("https://hdjq4n85yi.execute-api.eu-west-1.amazonaws.com/Prod/getTime").toURL.openStream()
      val json = Json.parse(Source.fromInputStream(is).mkString)
      ZonedDateTime.parse((json \ "currentDate").as[String])
    } else {
      ZonedDateTime.now()
    }
    logger.info(s"Using date time: $zonedDateTime")
    zonedDateTime
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

    logContainer()

    val articlesMatches = for {
      footballDataResult <- footballData.pollFootballData(getZonedDateTime())
      articleMatches <- articleSearcher.tryToMatchWithCapiArticle(footballDataResult)
    } yield articleMatches

    val notificationsProcessing = articlesMatches.flatMap(notificationHandler.process)
    val liveActivitiesProcessing = articlesMatches.flatMap(liveActivityHandler.process)

    // we're in a lambda so we do need to block the main thread until processing is finished
    val result = Await.ready(Future.sequence(List(notificationsProcessing, liveActivitiesProcessing)), Duration(40, TimeUnit.SECONDS))
//    val result = Await.ready(articlesMatches, Duration(40, TimeUnit.SECONDS))

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

class NotificationHandler(configuration: Configuration, apiClient: NotificationsApiClient, dynamoDBClient: AmazonDynamoDBAsync, tableName: String) extends Logging {

  lazy val notificationSender = new NotificationSender(apiClient)

  lazy val matchStatusNotificationBuilder = new MatchStatusNotificationBuilder(configuration.mapiHost)

  lazy val eventConsumer = new EventConsumer(matchStatusNotificationBuilder)

  lazy val distinctCheck = new DynamoDistinctCheck[NotificationPayload, DynamoMatchNotification](
    client = dynamoDBClient,
    tableName = tableName,
    partitionKeyName = "notificationId",
    toDynamoModel = payload => DynamoMatchNotification(payload)
  )
  lazy val eventFilter = new EventFilter[NotificationPayload, DynamoMatchNotification](distinctCheck)

  def process(rawEvents: List[MatchDataWithArticle]): Future[Unit] = {
    val notifications = rawEvents.flatMap(eventConsumer.eventsToNotifications)
    for {
      filteredNotifications <- eventFilter.filterDynamoEvents(notifications) // todo what filtering is happening exactly?
      result <- notificationSender.sendNotifications(filteredNotifications)
    } yield result
  }  
}

class LiveActivityHandler(configuration: Configuration, dynamoDBClient: AmazonDynamoDBAsync, tableName: String) extends Logging {

  lazy val liveActivityPusher = new LiveActivityPusher()

  lazy val matchStatusLiveActivityPayloadBuilder = new MatchStatusLiveActivityPayloadBuilder(configuration.mapiHost)

  lazy val liveActivityEventConsumer = new LiveActivityEventConsumer(matchStatusLiveActivityPayloadBuilder)

  lazy val liveActivityDistinctCheck = new DynamoDistinctCheck[LiveActivityPayload, DynamoMatchLiveActivity](
    client = dynamoDBClient,
    tableName = tableName,
    partitionKeyName = "id",
    toDynamoModel = payload => DynamoMatchLiveActivity(payload)
  )
  lazy val liveActivityEventFilter = new EventFilter[LiveActivityPayload, DynamoMatchLiveActivity](liveActivityDistinctCheck)

  def process(rawEvents: List[MatchDataWithArticle]): Future[Unit] = {
    val liveActivities = rawEvents.flatMap(liveActivityEventConsumer.eventsToLiveActivityPayload)
    for {
      filteredLiveActivities <- liveActivityEventFilter.filterDynamoEvents(liveActivities)
      result <- liveActivityPusher.pushEvents(filteredLiveActivities)
    } yield result
  }  
}