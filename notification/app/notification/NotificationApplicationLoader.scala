package notification

import _root_.controllers.AssetsComponents
import _root_.models.{NewsstandShardConfig, TopicCount}
import org.apache.pekko.actor.ActorSystem
import aws.{AsyncDynamo, TopicCountsS3}
import com.amazonaws.regions.Regions.EU_WEST_1
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import com.gu.AppIdentity
import com.gu.notificationschedule.dynamo.{NotificationSchedulePersistenceAsync, NotificationSchedulePersistenceImpl}
import com.softwaremill.macwire._
import metrics.CloudWatchMetrics
import notification.authentication.NotificationAuthAction
import notification.controllers.{Main, Schedule}
import notification.data.{CachingDataStore, S3DataStore}
import notification.services.guardian.{GuardianNotificationSender, ReportTopicRegistrationCounter, TopicRegistrationCounter}
import notification.services.{NewsstandSender, _}
import play.api.ApplicationLoader.Context
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.api.{BuiltInComponents, BuiltInComponentsFromContext}
import play.filters.HttpFiltersComponents
import play.filters.gzip.GzipFilter
import play.filters.hosts.AllowedHostsFilter
import router.Routes
import tracking.NotificationReportRepository
import utils.{CustomApplicationLoader, MobileAwsCredentialsProvider}

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

  lazy val appConfig = new Configuration(configuration, identity)
  lazy val metrics = new CloudWatchMetrics(applicationLifecycle, environment, identity)

  lazy val authAction = wire[NotificationAuthAction]

  val credentialsProvider = new MobileAwsCredentialsProvider()

  val asyncDynamo: AsyncDynamo = AsyncDynamo(EU_WEST_1, credentialsProvider)

  lazy val notificationReportRepository = new NotificationReportRepository(asyncDynamo, appConfig.dynamoReportsTableName)

  lazy val notificationSchedulePersistence: NotificationSchedulePersistenceAsync = new NotificationSchedulePersistenceImpl(appConfig.dynamoScheduleTableName, asyncDynamo.client)

  lazy val newsstandNotificationSender: NewsstandSender = {
    new NewsstandSender(
      NewsstandShardConfig(appConfig.newsstandShards),
      notificationSchedulePersistence)
  }

  lazy val s3Client: AmazonS3 = {
    AmazonS3ClientBuilder.standard()
      .withRegion(EU_WEST_1)
      .withCredentials(credentialsProvider)
      .build()
  }

  lazy val topicCountsS3 = new TopicCountsS3(s3Client, configuration.get[String]("notifications.topicCounts.bucket"), s"${appConfig.stage}/${configuration.get[String]("notifications.topicCounts.fileName")}")

  lazy val topicCountCacheingDataStore: CachingDataStore[TopicCount] = new CachingDataStore[TopicCount](
    new S3DataStore[TopicCount](topicCountsS3)
  )

  lazy val topicRegistrationCounter: TopicRegistrationCounter = new ReportTopicRegistrationCounter(topicCountCacheingDataStore)

  lazy val sqsClient: AmazonSQSAsync = AmazonSQSAsyncClientBuilder.standard()
    .withCredentials(credentialsProvider)
    .withRegion(EU_WEST_1)
    .build()

  lazy val notificationSender: NotificationSender = new GuardianNotificationSender(
    sqsClient = sqsClient,
    registrationCounter = topicRegistrationCounter,
    harvesterSqsUrl = configuration.get[String]("notifications.queues.harvester")
  )

  lazy val sloTrackingSender :SloTrackingSender = new SloTrackingSender(sqsClient, appConfig.notificationSloQueueUrl)

  lazy val fastlyPurge: FastlyPurge = wire[FastlyPurgeImpl]
  lazy val articlePurge: ArticlePurge = wire[ArticlePurge]

  lazy val mainController = wire[Main]
  lazy val scheduleController = wire[Schedule]
  lazy val router: Router = wire[Routes]

}


