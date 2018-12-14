package registration

import java.io.ByteArrayInputStream

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
import cats.effect.IO
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.gu.AppIdentity
import metrics.{CloudWatchMetrics, Metrics}
import registration.services.fcm.FcmRegistrar

import scala.concurrent.Future

class RegistrationApplicationLoader extends CustomApplicationLoader {
  def buildComponents(identity: AppIdentity, context: Context): BuiltInComponents = new RegistrationApplicationComponents(identity, context)
}

class RegistrationApplicationComponents(identity: AppIdentity, context: Context) extends BuiltInComponentsFromContext(context)
  with AhcWSComponents
  with HttpFiltersComponents
  with AssetsComponents {

  implicit val implicitActorSystem: ActorSystem = actorSystem

  override def httpFilters: Seq[EssentialFilter] = super.httpFilters.filterNot{ filter => filter.getClass == classOf[AllowedHostsFilter] }

  lazy val appConfig = new Configuration(configuration)
  lazy val metrics: Metrics = new CloudWatchMetrics(applicationLifecycle, environment, identity)

  val credentialsProvider = new MobileAwsCredentialsProvider()

  lazy val topicSubscriptionsRepository: TopicSubscriptionsRepository = {
    val underlying = new DynamoTopicSubscriptionsRepository(AsyncDynamo(EU_WEST_1, credentialsProvider), appConfig.dynamoTopicsTableName)
    val batching = new BatchingTopicSubscriptionsRepository(underlying)
    batching.scheduleFlush(appConfig.dynamoTopicsFlushInterval)
    batching
  }

  lazy val subscriptionTracker: SubscriptionTracker = wire[SubscriptionTracker]

  lazy val defaultHubClient = new NotificationHubClient(appConfig.defaultHub, wsClient)
  lazy val gcmNotificationRegistrar: GCMNotificationRegistrar = new GCMNotificationRegistrar(defaultHubClient, subscriptionTracker, metrics)
  lazy val apnsNotificationRegistrar: APNSNotificationRegistrar = new APNSNotificationRegistrar(defaultHubClient, subscriptionTracker, metrics)

  lazy val newsstandHubClient = new NotificationHubClient(appConfig.newsstandHub, wsClient)
  lazy val newsstandNotificationRegistrar: NewsstandNotificationRegistrar = new NewsstandNotificationRegistrar(newsstandHubClient, subscriptionTracker, metrics)
  lazy val legacyNewsstandRegistrationConverterConfig:NewsstandShardConfig = NewsstandShardConfig(appConfig.newsstandShards)

  lazy val firebaseMessaging: FirebaseMessaging = {
    val firebaseOptions: FirebaseOptions = new FirebaseOptions.Builder()
      .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(appConfig.firebaseServiceAccountKey.getBytes)))
      .setDatabaseUrl(appConfig.firebaseDatabaseUrl)
      .build

    val fApp = FirebaseApp.initializeApp(firebaseOptions)
    applicationLifecycle.addStopHook(() => Future(fApp.delete()))
    FirebaseMessaging.getInstance(fApp)
  }

  lazy val fcmNotificationRegistrar: FcmRegistrar = new FcmRegistrar(
    firebaseMessaging = firebaseMessaging,
    ws = wsClient,
    configuration = appConfig,
    metrics = metrics,
    fcmExecutionContext = actorSystem.dispatchers.lookup("fcm-io") // FCM calls are blocking
  )


  lazy val registrarProvider: RegistrarProvider = new AzureRegistrarProvider(gcmNotificationRegistrar, apnsNotificationRegistrar, newsstandNotificationRegistrar)
  lazy val migratingRegistrarProvider: RegistrarProvider = new MigratingRegistrarProvider(registrarProvider, fcmNotificationRegistrar, metrics)

  lazy val auditorGroup: AuditorGroup = {
    AuditorGroup(Set(
      FootballMatchAuditor(new WSPaClient(appConfig.auditorConfiguration.paApiConfig, wsClient)),
      LiveblogAuditor(wsClient, appConfig.auditorConfiguration.contentApiConfig),
      TimeExpiringAuditor(Set(Topic(ElectionResults, "us-presidential-2016")), DateTime.parse("2016-11-30T00:00:00Z"))
    ))
  }

  lazy val topicValidator: TopicValidator = wire[AuditorTopicValidator]
  lazy val legacyRegistrationConverter = wire[LegacyRegistrationConverter]
  lazy val legacyNewsstandRegistrationConverter: LegacyNewsstandRegistrationConverter = wire[LegacyNewsstandRegistrationConverter]

  lazy val registrationDbService: db.RegistrationService[IO, fs2.Stream] = db.RegistrationService.fromConfig(configuration, applicationLifecycle)
  lazy val databaseRegistrar: NotificationRegistrar = new DatabaseRegistrar(registrationDbService, metrics)

  lazy val copyingRegistrarProvider: RegistrarProvider = new CopyingRegistrarProvider(
    azureRegistrarProvider = migratingRegistrarProvider,
    databaseRegistrar = databaseRegistrar,
    metrics = metrics
  )

  lazy val mainController = new Main(databaseRegistrar, topicValidator, legacyRegistrationConverter, legacyNewsstandRegistrationConverter, appConfig, controllerComponents)

  override lazy val router: Router = wire[Routes]
  lazy val prefix: String = "/"
}