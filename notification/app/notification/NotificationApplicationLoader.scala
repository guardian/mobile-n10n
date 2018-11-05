package notification

import java.io.ByteArrayInputStream
import java.net.URI

import _root_.controllers.AssetsComponents
import akka.actor.ActorSystem
import aws.AsyncDynamo
import com.amazonaws.regions.Regions.EU_WEST_1
import azure.NotificationHubClient
import com.softwaremill.macwire._
import controllers.Main
import _root_.models.NewsstandShardConfig
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.gu.AppIdentity
import com.gu.notificationschedule.dynamo.NotificationSchedulePersistenceImpl
import _root_.models.{Android, Newsstand, iOS}
import notification.authentication.NotificationAuthAction
import notification.services.frontend.{FrontendAlerts, FrontendAlertsConfig}
import notification.services._
import notification.services.azure._
import notification.services.fcm.{APNSConfigConverter, AndroidConfigConverter, FCMNotificationSender}
import notification.services.guardian.{GuardianNotificationSender, ReportTopicRegistrationCounter, TopicRegistrationCounter}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import play.api.{BuiltInComponents, BuiltInComponentsFromContext}
import play.api.ApplicationLoader.Context
import play.api.mvc.EssentialFilter
import play.filters.HttpFiltersComponents
import play.filters.hosts.AllowedHostsFilter
import tracking.{BatchingTopicSubscriptionsRepository, DynamoNotificationReportRepository, DynamoTopicSubscriptionsRepository, TopicSubscriptionsRepository}
import utils.{CustomApplicationLoader, MobileAwsCredentialsProvider}
import router.Routes

import scala.concurrent.Future

class NotificationApplicationLoader extends CustomApplicationLoader {
  def buildComponents(identity: AppIdentity, context: Context): BuiltInComponents = new NotificationApplicationComponents(context)
}

class NotificationApplicationComponents(context: Context) extends BuiltInComponentsFromContext(context)
  with AhcWSComponents
  with HttpFiltersComponents
  with AssetsComponents {

  lazy val prefix: String = "/"

  override def httpFilters: Seq[EssentialFilter] = super.httpFilters.filterNot{ filter => filter.getClass == classOf[AllowedHostsFilter] }

  implicit lazy val implicitActorSystem: ActorSystem = actorSystem

  lazy val appConfig = new Configuration(configuration)

  lazy val authAction = wire[NotificationAuthAction]

  val credentialsProvider = new MobileAwsCredentialsProvider()

  val asyncDynamo: AsyncDynamo = AsyncDynamo(EU_WEST_1, credentialsProvider)

  lazy val topicSubscriptionsRepository: TopicSubscriptionsRepository = {
    val underlying = new DynamoTopicSubscriptionsRepository(asyncDynamo, appConfig.dynamoTopicsTableName)
    val batching = new BatchingTopicSubscriptionsRepository(underlying)
    batching.scheduleFlush(appConfig.dynamoTopicsFlushInterval)
    batching
  }

  lazy val hubClient = new NotificationHubClient(appConfig.defaultHub, wsClient)

  lazy val notificationReportRepository = new DynamoNotificationReportRepository(asyncDynamo, appConfig.dynamoReportsTableName)

  lazy val gcmNotificationSender: GCMSender = new GCMSender(hubClient, appConfig, topicSubscriptionsRepository)

  lazy val apnsNotificationSender: APNSSender = new APNSSender(hubClient, appConfig, topicSubscriptionsRepository)
  lazy val newsstandHubClient = new NotificationHubClient(appConfig.newsstandHub, wsClient)
  lazy val newsstandNotificationSender: NewsstandSender = {
    new NewsstandSender(
      newsstandHubClient,
      NewsstandShardConfig(appConfig.newsstandShards),
      new NotificationSchedulePersistenceImpl(appConfig.dynamoScheduleTableName, asyncDynamo.client))
  }
  lazy val newsstandShardNotificationSender: NewsstandShardSender = new NewsstandShardSender(newsstandHubClient,appConfig, topicSubscriptionsRepository)

  lazy val firebaseMessaging: FirebaseMessaging = {
    val firebaseOptions: FirebaseOptions = new FirebaseOptions.Builder()
      .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(appConfig.firebaseServiceAccountKey.getBytes)))
      .setDatabaseUrl(appConfig.firebaseDatabaseUrl)
      .build

    val fApp = FirebaseApp.initializeApp(firebaseOptions)
    applicationLifecycle.addStopHook(() => Future(fApp.delete()))
    FirebaseMessaging.getInstance(fApp)
  }

  lazy val androidConfigConverter: AndroidConfigConverter = wire[AndroidConfigConverter]
  lazy val apnsConfigConverter: APNSConfigConverter = wire[APNSConfigConverter]
  lazy val fcmNotificationSender: FCMNotificationSender = new FCMNotificationSender(
    apnsConfigConverter,
    androidConfigConverter,
    firebaseMessaging,
    actorSystem.dispatchers.lookup("fcm-io") // FCM calls are blocking
  )

  lazy val frontendAlerts: NotificationSender = {
    val frontendConfig = FrontendAlertsConfig(new URI(appConfig.frontendNewsAlertEndpoint), appConfig.frontendNewsAlertApiKey)
    new FrontendAlerts(frontendConfig, wsClient)
  }

  lazy val topicRegistrationCounter: TopicRegistrationCounter = new ReportTopicRegistrationCounter(
    wsClient,
    configuration.get[String]("report.url"),
    configuration.get[String]("report.apiKey")
  )

  lazy val sqsClient: AmazonSQSAsync = AmazonSQSAsyncClientBuilder.standard()
    .withCredentials(credentialsProvider)
    .withRegion(EU_WEST_1)
    .build()

  lazy val guardianIosNotificationSender: GuardianNotificationSender = new GuardianNotificationSender(
    sqsClient = sqsClient,
    registrationCounter = topicRegistrationCounter,
    platform = iOS,
    sqsUrl = configuration.get[String]("notifications.queues.ios")
  )

  lazy val guardianAndroidNotificationSender: GuardianNotificationSender = new GuardianNotificationSender(
    sqsClient = sqsClient,
    registrationCounter = topicRegistrationCounter,
    platform = Android,
    sqsUrl = configuration.get[String]("notifications.queues.android")
  )

  lazy val guardianNewsstandNotificationSender: GuardianNotificationSender = new GuardianNotificationSender(
    sqsClient = sqsClient,
    registrationCounter = topicRegistrationCounter,
    platform = Newsstand,
    sqsUrl = configuration.get[String]("notifications.queues.ios")
  )

  def withFilter(notificationSender: NotificationSender, invertCondition: Boolean): NotificationSender =
    new FilteredNotificationSender(notificationSender, topicRegistrationCounter, invertCondition)

  lazy val notificationSenders = List(
    withFilter(guardianIosNotificationSender, invertCondition = false),
    withFilter(guardianAndroidNotificationSender, invertCondition = false),
    withFilter(guardianNewsstandNotificationSender, invertCondition = false),
    gcmNotificationSender,
    apnsNotificationSender,
    newsstandShardNotificationSender,
    //frontendAlerts, //disabled until frontend decides whether to fix this feature or not.
    fcmNotificationSender
  )

  lazy val mainController = wire[Main]
  lazy val router: Router = wire[Routes]

}


