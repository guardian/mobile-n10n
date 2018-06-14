package registration

import _root_.controllers.AssetsComponents
import akka.actor.ActorSystem
import auditor.{AuditorGroup, FootballMatchAuditor, LiveblogAuditor, TimeExpiringAuditor}
import azure.NotificationHubClient
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import play.api.{BuiltInComponents, BuiltInComponentsFromContext}
import play.api.ApplicationLoader.Context
import com.softwaremill.macwire._
import _root_.models.Topic
import _root_.models.TopicTypes.ElectionResults
import aws.AsyncDynamo
import com.amazonaws.regions.Regions.EU_WEST_1
import org.joda.time.DateTime
import controllers.Main
import play.api.mvc.EssentialFilter
import play.filters.HttpFiltersComponents
import play.filters.hosts.AllowedHostsFilter
import registration.services.topic.{AuditorTopicValidator, TopicValidator}
import registration.services.azure.{APNSNotificationRegistrar, GCMNotificationRegistrar, NewsstandNotificationRegistrar}
import registration.services._
import tracking.{BatchingTopicSubscriptionsRepository, DynamoTopicSubscriptionsRepository, SubscriptionTracker, TopicSubscriptionsRepository}
import utils.{CustomApplicationLoader, MobileAwsCredentialsProvider}
import router.Routes
import _root_.models.NewsstandShardConfig

class RegistrationApplicationLoader extends CustomApplicationLoader {
  def buildComponents(context: Context) : BuiltInComponents = new RegistrationApplicationComponents(context)
}

class RegistrationApplicationComponents(context: Context) extends BuiltInComponentsFromContext(context)
  with AhcWSComponents
  with HttpFiltersComponents
  with AssetsComponents {

  implicit val implicitActorSystem: ActorSystem = actorSystem

  override def httpFilters: Seq[EssentialFilter] = super.httpFilters.filterNot{ filter => filter.getClass == classOf[AllowedHostsFilter] }

  lazy val appConfig = new Configuration(configuration)

  val credentialsProvider = new MobileAwsCredentialsProvider()

  lazy val mainController = wire[Main]
  lazy val topicSubscriptionsRepository: TopicSubscriptionsRepository = {
    val underlying = new DynamoTopicSubscriptionsRepository(AsyncDynamo(EU_WEST_1, credentialsProvider), appConfig.dynamoTopicsTableName)
    val batching = new BatchingTopicSubscriptionsRepository(underlying)
    batching.scheduleFlush(appConfig.dynamoTopicsFlushInterval)
    batching
  }
  lazy val subscriptionTracker: SubscriptionTracker = wire[SubscriptionTracker]

  lazy val defaultHubClient = new NotificationHubClient(appConfig.defaultHub, wsClient)

  lazy val registrarProvider: RegistrarProvider = wire[NotificationRegistrarProvider]
  lazy val gcmNotificationRegistrar: GCMNotificationRegistrar = new GCMNotificationRegistrar(defaultHubClient, subscriptionTracker)
  lazy val apnsNotificationRegistrar: APNSNotificationRegistrar = new APNSNotificationRegistrar(defaultHubClient, subscriptionTracker)

  lazy val newsstandHubClient = new NotificationHubClient(appConfig.newsstandHub, wsClient)
  lazy val newsstandNotificationRegistrar: NewsstandNotificationRegistrar = new NewsstandNotificationRegistrar(newsstandHubClient, subscriptionTracker)
  lazy val legacyNewsstandRegistrationConverterConfig:NewsstandShardConfig = NewsstandShardConfig(appConfig.newsstandShards)
  lazy val auditorGroup: AuditorGroup = {
    AuditorGroup(Set(
      FootballMatchAuditor(new WSPaClient(appConfig.auditorConfiguration.paApiConfig, wsClient)),
      LiveblogAuditor(wsClient, appConfig.auditorConfiguration.contentApiConfig),
      TimeExpiringAuditor(Set(Topic(ElectionResults, "us-presidential-2016")), DateTime.parse("2016-11-30T00:00:00Z"))
    ))
  }
  lazy val topicValidator: TopicValidator = wire[AuditorTopicValidator]
  lazy val legacyRegistrationConverter = wire[LegacyRegistrationConverter]
  lazy val legacyNewsstandRegistrationConverter = wire[LegacyNewsstandRegistrationConverter]

  override lazy val router: Router = wire[Routes]
  lazy val prefix: String = "/"
}