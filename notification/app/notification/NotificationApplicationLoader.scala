package notification

import java.net.URI

import _root_.controllers.Assets
import akka.actor.ActorSystem
import azure.{NotificationHubClient, NotificationHubConnection}
import com.softwaremill.macwire._
import notification.controllers.Main
import notification.services.frontend.{FrontendAlerts, FrontendAlertsConfig}
import notification.services._
import notification.services.azure._
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponents, BuiltInComponentsFromContext, LoggerConfigurator}
import play.api.ApplicationLoader.Context
import router.Routes
import tracking.{BatchingTopicSubscriptionsRepository, DynamoNotificationReportRepository, DynamoTopicSubscriptionsRepository, TopicSubscriptionsRepository}

import scala.concurrent.ExecutionContext

class NotificationApplicationLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader) foreach { _.configure(context.environment) }
    (new BuiltInComponentsFromContext(context) with AppComponents).application
  }
}

trait AppComponents extends PlayComponents
  with AhcWSComponents
  with Controllers
  with AzureHubComponents
  with FrontendAlertsComponents
  with ConfigurationComponents
  with ExecutionEnv


trait Controllers {
  self: AzureHubComponents with FrontendAlertsComponents with ConfigurationComponents with PlayComponents with ExecutionEnv =>
  lazy val notificationSenders = List(
    wnsNotificationSender,
    gcmNotificationSender,
    apnsNotificationSender,
    frontendAlerts,
    apnsEnterpriseNotificationSender
  )
  lazy val mainController = wire[Main]
}

trait ConfigurationComponents {
  lazy val appConfig = new Configuration
}

trait PlayComponents extends BuiltInComponents {
  self: Controllers =>
  lazy val assets: Assets = wire[Assets]
  lazy val router: Router = wire[Routes]
  lazy val prefix: String = "/"
}

trait AzureHubComponents {
  self: ConfigurationComponents with AhcWSComponents with ExecutionEnv =>

  import com.amazonaws.regions.Regions.EU_WEST_1
  import aws.AsyncDynamo

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
    val hubClient = new NotificationHubClient(appConfig.enterpriseHub, wsClient)
    new NewsstandSender(hubClient)
  }

  lazy val apnsEnterpriseNotificationSender: APNSEnterpriseSender = {
    val enterpriseHubClient = new NotificationHubClient(appConfig.enterpriseHub, wsClient)
    new APNSEnterpriseSender(enterpriseHubClient, appConfig, topicSubscriptionsRepository)
  }
}


trait FrontendAlertsComponents {
  self: ConfigurationComponents with AhcWSComponents with ExecutionEnv =>

  lazy val frontendAlerts: NotificationSender = {
    val frontendConfig = FrontendAlertsConfig(new URI(appConfig.frontendNewsAlertEndpoint), appConfig.frontendNewsAlertApiKey)
    new FrontendAlerts(frontendConfig, wsClient)
  }

}

trait ExecutionEnv {
  self: PlayComponents =>
  implicit lazy val executionContext: ExecutionContext = actorSystem.dispatcher
  implicit lazy val implicitActorSystem: ActorSystem = actorSystem
}


