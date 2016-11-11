package registration

import java.net.URL

import _root_.controllers.Assets
import akka.actor.ActorSystem
import auditor.{Auditor, AuditorGroup, LiveblogAuditor, RemoteAuditor, TimeExpiringAuditor}
import azure.{NotificationHubClient, NotificationHubConnection}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponents, BuiltInComponentsFromContext, LoggerConfigurator}
import play.api.ApplicationLoader.Context
import com.softwaremill.macwire._
import _root_.models.Topic
import _root_.models.TopicTypes.ElectionResults
import org.joda.time.DateTime
import registration.controllers.Main
import registration.services.topic.{AuditorTopicValidator, TopicValidator}
import registration.services.azure.{APNSEnterpriseNotifcationRegistrar, APNSNotificationRegistrar, GCMNotificationRegistrar, NewsstandNotificationRegistrar, WindowsNotificationRegistrar}
import registration.services._
import tracking.{BatchingTopicSubscriptionsRepository, DynamoTopicSubscriptionsRepository, SubscriptionTracker, TopicSubscriptionsRepository}

import scala.concurrent.ExecutionContext

import router.Routes

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
  with APNSEnterpriseRegistrations
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
    with APNSEnterpriseRegistrations
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

trait APNSEnterpriseRegistrations {
  self: Tracking
    with AppConfiguration
    with AhcWSComponents
    with ExecutionEnv =>

  lazy val enterpriseHubClient = new NotificationHubClient(appConfig.enterpriseHub, wsClient)

  lazy val apnsEnterpriseNotificationRegistrar: APNSEnterpriseNotifcationRegistrar = new APNSEnterpriseNotifcationRegistrar(enterpriseHubClient, subscriptionTracker)
}

trait NewsstandRegistrations {
  self: Tracking
    with AppConfiguration
    with AhcWSComponents
    with ExecutionEnv =>

  lazy val newsstandHubClient = new NotificationHubClient(appConfig.newsstandHub, wsClient)

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
  lazy val auditorGroup: AuditorGroup = {
    val remoteAuditors: Set[Auditor] = appConfig.auditorConfiguration.hosts map { host => RemoteAuditor(new URL(host), wsClient) }
    AuditorGroup(
      remoteAuditors +
        LiveblogAuditor(wsClient, appConfig.auditorConfiguration.contentApiConfig) +
        TimeExpiringAuditor(Set(Topic(ElectionResults, "us-presidential-2016")), DateTime.parse("2016-11-30T00:00:00Z"))
    )
  }
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
