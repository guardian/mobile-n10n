package registration

import _root_.controllers.Assets
import akka.actor.ActorSystem
import auditor.AuditorWSClient
import azure.{NotificationHubClient, NotificationHubConnection}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponents, BuiltInComponentsFromContext, LoggerConfigurator}
import play.api.ApplicationLoader.Context
import com.softwaremill.macwire._
import registration.controllers.Main
import registration.services.topic.{AuditorTopicValidator, TopicValidator}
import registration.services.azure.{APNSNotificationRegistrar, GCMNotificationRegistrar, NewsstandNotificationRegistrar, WindowsNotificationRegistrar}
import router.Routes
import registration.services._
import tracking.{BatchingTopicSubscriptionsRepository, DynamoTopicSubscriptionsRepository, SubscriptionTracker, TopicSubscriptionsRepository}

import scala.concurrent.ExecutionContext

class RegistrationApplicationLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader) foreach { _.configure(context.environment) }
    (new BuiltInComponentsFromContext(context) with AppComponents).application
  }
}

trait AppComponents extends Controllers
  with Registrars
  with LegacyComponents
  with WindowsRegistrations
  with GCMRegistrations
  with APNSRegistrations
  with NewsstandRegistrations
  with NotificationsHubClient
  with Tracking
  with TopicValidation
  with AppConfiguration
  with PlayComponents
  with AhcWSComponents
  with ExecutionEnv

trait Controllers {
  self: Registrars
    with TopicValidation
    with PlayComponents
    with AppConfiguration
    with LegacyComponents
    with ExecutionEnv =>
  lazy val mainController = wire[Main]
}

trait AppConfiguration {
  lazy val appConfig = new Configuration
}

trait Registrars {
  self: WindowsRegistrations
    with GCMRegistrations
    with APNSRegistrations
    with NewsstandRegistrations
    with ExecutionEnv =>
  lazy val registrarProvider: RegistrarProvider = wire[NotificationRegistrarProvider]
}

trait GCMRegistrations {
  self: Tracking
    with AppConfiguration
    with NotificationsHubClient
    with AhcWSComponents
    with ExecutionEnv =>

  lazy val gcmNotificationRegistrar: GCMNotificationRegistrar = wire[GCMNotificationRegistrar]
}

trait APNSRegistrations {
  self: Tracking
    with AppConfiguration
    with NotificationsHubClient
    with AhcWSComponents
    with ExecutionEnv =>

  lazy val apnsNotificationRegistrar: APNSNotificationRegistrar = wire[APNSNotificationRegistrar]
}

trait WindowsRegistrations {
  self: Tracking
    with AppConfiguration
    with NotificationsHubClient
    with AhcWSComponents
    with ExecutionEnv =>

  lazy val winNotificationRegistrar: WindowsNotificationRegistrar = wire[WindowsNotificationRegistrar]
}

trait NewsstandRegistrations {
  self: Tracking
    with AppConfiguration
    with AhcWSComponents
    with ExecutionEnv =>

  lazy val newsstandHubClient = new NotificationHubClient(appConfig.defaultHub, wsClient)

  lazy val newsstandNotificationRegistrar: NewsstandNotificationRegistrar = new NewsstandNotificationRegistrar(newsstandHubClient, subscriptionTracker)
}

trait NotificationsHubClient {
  self: AppConfiguration
    with AhcWSComponents
    with ExecutionEnv =>

  lazy val defaultHubClient = new NotificationHubClient(appConfig.defaultHub, wsClient)
}

trait Tracking {
  self: AppConfiguration
    with ExecutionEnv =>

  import com.amazonaws.regions.Regions.EU_WEST_1
  import aws.AsyncDynamo

  lazy val topicSubscriptionsRepository: TopicSubscriptionsRepository = {
    val underlying = new DynamoTopicSubscriptionsRepository(AsyncDynamo(EU_WEST_1), appConfig.dynamoTopicsTableName)
    val batching = new BatchingTopicSubscriptionsRepository(underlying)
    batching.scheduleFlush(appConfig.dynamoTopicsFlushInterval)
    batching
  }

  lazy val subscriptionTracker = wire[SubscriptionTracker]
}

trait TopicValidation {
  self: AppConfiguration
    with AhcWSComponents
    with ExecutionEnv =>
  lazy val auditorWsClient = wire[AuditorWSClient]
  lazy val topicValidator: TopicValidator = wire[AuditorTopicValidator]
}

trait LegacyComponents {
  self: AppConfiguration
    with AhcWSComponents
    with ExecutionEnv =>
  lazy val legacyRegistrationClient = wire[LegacyRegistrationClient]
  lazy val legacyRegistrationConverter = wire[LegacyRegistrationConverter]
  lazy val legacyNewsstandRegistrationConverter = wire[LegacyNewsstandRegistrationConverter]
}

trait PlayComponents extends BuiltInComponents {
  self: Controllers =>
  lazy val assets: Assets = wire[Assets]
  override lazy val router: Router = wire[Routes]
  lazy val prefix: String = "/"
}

trait ExecutionEnv {
  self: PlayComponents =>
  implicit lazy val executionContext: ExecutionContext = actorSystem.dispatcher
  implicit lazy val implicitActorSystem: ActorSystem = actorSystem
}
