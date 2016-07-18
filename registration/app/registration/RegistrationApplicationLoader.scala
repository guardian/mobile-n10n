package registration

import _root_.controllers.Assets
import auditor.AuditorWSClient
import azure.{NotificationHubClient, NotificationHubConnection}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import play.api.{BuiltInComponents, BuiltInComponentsFromContext, LoggerConfigurator, Application, ApplicationLoader}
import play.api.ApplicationLoader.Context
import com.softwaremill.macwire._
import registration.controllers.Main
import registration.services.topic.{TopicValidator, AuditorTopicValidator}
import registration.services.azure.{GCMNotificationRegistrar, WindowsNotificationRegistrar}
import registration.services.{RegistrarProvider, NotificationRegistrarProvider, Configuration}
import router.Routes
import tracking.{SubscriptionTracker, DynamoTopicSubscriptionsRepository}

import scala.concurrent.ExecutionContext

class RegistrationApplicationLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader) foreach { _.configure(context.environment) }
    (new BuiltInComponentsFromContext(context) with AppComponents).application
  }
}

trait AppComponents extends Controllers
  with Registrars
  with WindowsRegistrations
  with GCMRegistrations
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
    with ExecutionEnv =>
  lazy val mainController = wire[Main]
}

trait AppConfiguration {
  lazy val appConfig = new Configuration
}

trait Registrars {
  self: WindowsRegistrations
    with GCMRegistrations
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

trait WindowsRegistrations {
  self: Tracking
    with AppConfiguration
    with NotificationsHubClient
    with AhcWSComponents
    with ExecutionEnv =>

  lazy val winNotificationRegistrar: WindowsNotificationRegistrar = wire[WindowsNotificationRegistrar]
}

trait NotificationsHubClient {
  self: AppConfiguration
    with AhcWSComponents
    with ExecutionEnv =>

  lazy val hubClient = {
    val hubConnection = NotificationHubConnection(
      endpoint = appConfig.hubEndpoint,
      sharedAccessKeyName = appConfig.hubSharedAccessKeyName,
      sharedAccessKey = appConfig.hubSharedAccessKey
    )
    new NotificationHubClient(hubConnection, wsClient)
  }
}

trait Tracking {
  self: AppConfiguration
    with ExecutionEnv =>

  import com.amazonaws.regions.Regions.EU_WEST_1
  import aws.AsyncDynamo

  lazy val topicSubscriptionRepository = new DynamoTopicSubscriptionsRepository(
    AsyncDynamo(region = EU_WEST_1),
    appConfig.dynamoTopicsTableName
  )

  lazy val subscriptionTracker = wire[SubscriptionTracker]
}

trait TopicValidation {
  self: AppConfiguration
    with AhcWSComponents
    with ExecutionEnv =>
  lazy val auditorWsClient = wire[AuditorWSClient]
  lazy val topicValidator: TopicValidator = wire[AuditorTopicValidator]
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
}
