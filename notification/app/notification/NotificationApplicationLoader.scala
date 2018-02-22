package notification

import java.net.URI

import _root_.controllers.{Assets, AssetsComponents}
import akka.actor.ActorSystem
import aws.AsyncDynamo
import com.amazonaws.regions.Regions.EU_WEST_1
import azure.{NotificationHubClient, NotificationHubConnection}
import com.gu.AppIdentity
import com.gu.conf.{ConfigurationLoader, S3ConfigurationLocation}
import com.softwaremill.macwire._
import controllers.Main
import notification.authentication.NotificationAuthAction
import notification.services.frontend.{FrontendAlerts, FrontendAlertsConfig}
import notification.services._
import notification.services.azure._
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import play.api.{Configuration => PlayConfiguration}
import play.api.{Application, ApplicationLoader, BuiltInComponents, BuiltInComponentsFromContext, LoggerConfigurator}
import play.api.ApplicationLoader.Context
import play.api.mvc.EssentialFilter
import play.filters.HttpFiltersComponents
import play.filters.hosts.AllowedHostsFilter
import tracking.{BatchingTopicSubscriptionsRepository, DynamoNotificationReportRepository, DynamoTopicSubscriptionsRepository, TopicSubscriptionsRepository}

import router.Routes

class NotificationApplicationLoader extends ApplicationLoader {

  def buildComponents(context: Context) = new NotificationApplicationComponents(context)

  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader) foreach { _.configure(context.environment) }
    val identity = AppIdentity.whoAmI(defaultAppName = "notification", defaultStackName = "mobile-notification")
    val config = ConfigurationLoader.load(identity) {
      case AppIdentity(app, stack, stage, _) if (stage != "DEV") =>  S3ConfigurationLocation (
        bucket = "mobile-notifications-dist",
        path = s"$stage/$stack/$app.conf"
      )
    }
    val loadedConfig = PlayConfiguration(config)
    val newContext = context.copy( initialConfiguration = context.initialConfiguration ++ loadedConfig )
    buildComponents(newContext).application
  }
}

class NotificationApplicationComponents(context: Context) extends BuiltInComponentsFromContext(context)
  with AhcWSComponents
  with HttpFiltersComponents
  with AssetsComponents {

  override def httpFilters: Seq[EssentialFilter] = super.httpFilters.filterNot{ filter => filter.getClass == classOf[AllowedHostsFilter] }

  implicit lazy val implicitActorSystem: ActorSystem = actorSystem

  lazy val appConfig = new Configuration

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

  lazy val topicSubscriptionsRepository: TopicSubscriptionsRepository = {
    val underlying = new DynamoTopicSubscriptionsRepository(AsyncDynamo(EU_WEST_1), appConfig.dynamoTopicsTableName)
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
    new NewsstandSender(hubClient)
  }

  lazy val frontendAlerts: NotificationSender = {
    val frontendConfig = FrontendAlertsConfig(new URI(appConfig.frontendNewsAlertEndpoint), appConfig.frontendNewsAlertApiKey)
    new FrontendAlerts(frontendConfig, wsClient)
  }

}


