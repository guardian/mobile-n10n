package notification

import java.net.URI

import _root_.controllers.AssetsComponents
import akka.actor.ActorSystem
import aws.AsyncDynamo
import com.amazonaws.regions.Regions.EU_WEST_1
import azure.NotificationHubClient
import com.softwaremill.macwire._
import controllers.Main
import _root_.models.NewsstandShardConfig
import com.gu.notificationschedule.dynamo.{NotificationSchedulePersistenceImpl, ScheduleTableConfig}
import notification.authentication.NotificationAuthAction
import notification.services.frontend.{FrontendAlerts, FrontendAlertsConfig}
import notification.services._
import notification.services.azure._
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import play.api.BuiltInComponentsFromContext
import play.api.ApplicationLoader.Context
import play.api.mvc.EssentialFilter
import play.filters.HttpFiltersComponents
import play.filters.hosts.AllowedHostsFilter
import tracking.{BatchingTopicSubscriptionsRepository, DynamoNotificationReportRepository, DynamoTopicSubscriptionsRepository, TopicSubscriptionsRepository}
import utils.CustomApplicationLoader

import router.Routes

class NotificationApplicationLoader extends CustomApplicationLoader {
  def buildComponents(context: Context) = new NotificationApplicationComponents(context)
}

class NotificationApplicationComponents(context: Context) extends BuiltInComponentsFromContext(context)
  with AhcWSComponents
  with HttpFiltersComponents
  with AssetsComponents {

  override def httpFilters: Seq[EssentialFilter] = super.httpFilters.filterNot{ filter => filter.getClass == classOf[AllowedHostsFilter] }

  implicit lazy val implicitActorSystem: ActorSystem = actorSystem

  lazy val appConfig = new Configuration(configuration)

  lazy val authAction = wire[NotificationAuthAction]

  lazy val notificationSenders = List(
    wnsNotificationSender,
    gcmNotificationSender,
    apnsNotificationSender,
    frontendAlerts
  )
  lazy val mainController = wire[Main]
  lazy val router: Router = wire[Routes]

  lazy val prefix: String = "/"

  lazy val hubClient = new NotificationHubClient(appConfig.defaultHub, wsClient)

  val asyncDynamo: AsyncDynamo = AsyncDynamo(EU_WEST_1)
  lazy val topicSubscriptionsRepository: TopicSubscriptionsRepository = {
    val underlying = new DynamoTopicSubscriptionsRepository(asyncDynamo, appConfig.dynamoTopicsTableName)
    val batching = new BatchingTopicSubscriptionsRepository(underlying)
    batching.scheduleFlush(appConfig.dynamoTopicsFlushInterval)
    batching
  }

  lazy val notificationReportRepository = new DynamoNotificationReportRepository(AsyncDynamo(EU_WEST_1), appConfig.dynamoReportsTableName)

  lazy val wnsNotificationSender: WNSSender = wire[WNSSender]

  lazy val gcmNotificationSender: GCMSender = wire[GCMSender]

  lazy val apnsNotificationSender: APNSSender = wire[APNSSender]
  lazy val newsstandNotificationSender: NewsstandSender = {
    val hubClient = new NotificationHubClient(appConfig.newsstandHub, wsClient)
    new NewsstandSender(
      hubClient,
      NewsstandShardConfig(appConfig.newsstandShards),
      new NotificationSchedulePersistenceImpl(appConfig.dynamoScheduleTableName, asyncDynamo.client))
  }

  lazy val frontendAlerts: NotificationSender = {
    val frontendConfig = FrontendAlertsConfig(new URI(appConfig.frontendNewsAlertEndpoint), appConfig.frontendNewsAlertApiKey)
    new FrontendAlerts(frontendConfig, wsClient)
  }

}


