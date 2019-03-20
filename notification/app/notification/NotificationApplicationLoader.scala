package notification

import java.net.URI

import _root_.controllers.AssetsComponents
import akka.actor.ActorSystem
import aws.{AsyncDynamo, TopicCountsS3}
import com.amazonaws.regions.Regions.EU_WEST_1
import com.softwaremill.macwire._
import controllers.Main
import _root_.models.NewsstandShardConfig
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import com.gu.{AppIdentity, AwsIdentity}
import com.gu.notificationschedule.dynamo.NotificationSchedulePersistenceImpl
import _root_.models.{Android, Newsstand, iOS}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import metrics.CloudWatchMetrics
import _root_.models.TopicCount
import notification.authentication.NotificationAuthAction
import notification.data.{CachingDataStore, S3DataStore}
import notification.services.frontend.{FrontendAlerts, FrontendAlertsConfig}
import notification.services.{NewsstandSender, _}
import notification.services.guardian.{GuardianNotificationSender, ReportTopicRegistrationCounter, TopicRegistrationCounter}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import play.api.{BuiltInComponents, BuiltInComponentsFromContext}
import play.api.ApplicationLoader.Context
import play.api.mvc.EssentialFilter
import play.filters.HttpFiltersComponents
import play.filters.gzip.GzipFilter
import play.filters.hosts.AllowedHostsFilter
import utils.{CustomApplicationLoader, MobileAwsCredentialsProvider}
import router.Routes
import tracking.DynamoNotificationReportRepository

class NotificationApplicationLoader extends CustomApplicationLoader {
  def buildComponents(identity: AppIdentity, context: Context): BuiltInComponents = new NotificationApplicationComponents(identity, context)
}

class NotificationApplicationComponents(identity: AppIdentity, context: Context) extends BuiltInComponentsFromContext(context)
  with AhcWSComponents
  with HttpFiltersComponents
  with AssetsComponents {

  lazy val prefix: String = "/"

  val gzipFilter = new GzipFilter()
  override def httpFilters: Seq[EssentialFilter] = super.httpFilters.filterNot{ filter => filter.getClass == classOf[AllowedHostsFilter] } :+ gzipFilter

  implicit lazy val implicitActorSystem: ActorSystem = actorSystem

  lazy val appConfig = new Configuration(configuration)
  lazy val metrics = new CloudWatchMetrics(applicationLifecycle, environment, identity)

  lazy val authAction = wire[NotificationAuthAction]

  val credentialsProvider = new MobileAwsCredentialsProvider()

  val asyncDynamo: AsyncDynamo = AsyncDynamo(EU_WEST_1, credentialsProvider)

  lazy val notificationReportRepository = new DynamoNotificationReportRepository(asyncDynamo, appConfig.dynamoReportsTableName)

  lazy val newsstandNotificationSender: NewsstandSender = {
    new NewsstandSender(
      NewsstandShardConfig(appConfig.newsstandShards),
      new NotificationSchedulePersistenceImpl(appConfig.dynamoScheduleTableName, asyncDynamo.client))
  }

  lazy val frontendAlerts: NotificationSender = {
    val frontendConfig = FrontendAlertsConfig(new URI(appConfig.frontendNewsAlertEndpoint), appConfig.frontendNewsAlertApiKey)
    new FrontendAlerts(frontendConfig, wsClient)
  }

  lazy val s3Client: AmazonS3 = {
    AmazonS3ClientBuilder.standard()
      .withRegion(EU_WEST_1)
      .withCredentials(credentialsProvider)
      .build()
  }

  lazy val stage = identity match {
    case AwsIdentity(_, _, stage, _) => stage
    case _ => "DEV"
  }

  lazy val topicCountsS3 = new TopicCountsS3(s3Client, configuration.get[String]("notifications.topicCounts.bucket"), s"${stage}/${configuration.get[String]("notifications.topicCounts.fileName")}")
  
  lazy val topicCountCacheingDataStore: CachingDataStore[TopicCount] = new CachingDataStore[TopicCount](
    new S3DataStore[TopicCount](topicCountsS3)
  )

  lazy val topicRegistrationCounter: TopicRegistrationCounter = new ReportTopicRegistrationCounter(topicCountCacheingDataStore)

  lazy val sqsClient: AmazonSQSAsync = AmazonSQSAsyncClientBuilder.standard()
    .withCredentials(credentialsProvider)
    .withRegion(EU_WEST_1)
    .build()

  lazy val guardianIosNotificationSender: GuardianNotificationSender = new GuardianNotificationSender(
    sqsClient = sqsClient,
    registrationCounter = topicRegistrationCounter,
    platform = iOS,
    harvesterSqsUrl = configuration.get[String]("notifications.queues.harvester")
  )

  lazy val guardianAndroidNotificationSender: GuardianNotificationSender = new GuardianNotificationSender(
    sqsClient = sqsClient,
    registrationCounter = topicRegistrationCounter,
    platform = Android,
    harvesterSqsUrl = configuration.get[String]("notifications.queues.harvester")
  )

  lazy val guardianNewsstandNotificationSender: GuardianNotificationSender = new GuardianNotificationSender(
    sqsClient = sqsClient,
    registrationCounter = topicRegistrationCounter,
    platform = Newsstand,
    harvesterSqsUrl = configuration.get[String]("notifications.queues.harvester")
  )

  def withFilter(notificationSender: NotificationSender, invertCondition: Boolean): NotificationSender =
    new FilteredNotificationSender(notificationSender, topicRegistrationCounter, invertCondition)

  lazy val notificationSenders = List(
    guardianIosNotificationSender,
    guardianAndroidNotificationSender,
    guardianNewsstandNotificationSender,
  )

  lazy val mainController = wire[Main]
  lazy val router: Router = wire[Routes]

}


