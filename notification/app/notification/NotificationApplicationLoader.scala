package notification

import java.net.URI

import _root_.controllers.AssetsComponents
import akka.actor.ActorSystem
import aws.AsyncDynamo
import com.amazonaws.regions.Regions.EU_WEST_1
import com.softwaremill.macwire._
import controllers.Main
import _root_.models.NewsstandShardConfig
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import com.gu.AppIdentity
import com.gu.notificationschedule.dynamo.NotificationSchedulePersistenceImpl
import _root_.models.{Android, Newsstand, iOS}
import notification.authentication.NotificationAuthAction
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
  def buildComponents(identity: AppIdentity, context: Context): BuiltInComponents = new NotificationApplicationComponents(context)
}

class NotificationApplicationComponents(context: Context) extends BuiltInComponentsFromContext(context)
  with AhcWSComponents
  with HttpFiltersComponents
  with AssetsComponents {

  lazy val prefix: String = "/"

  val gzipFilter = new GzipFilter()
  override def httpFilters: Seq[EssentialFilter] = super.httpFilters.filterNot{ filter => filter.getClass == classOf[AllowedHostsFilter] } :+ gzipFilter

  implicit lazy val implicitActorSystem: ActorSystem = actorSystem

  lazy val appConfig = new Configuration(configuration)

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
    guardianIosNotificationSender,
    guardianAndroidNotificationSender,
    guardianNewsstandNotificationSender,
  )

  lazy val mainController = wire[Main]
  lazy val router: Router = wire[Routes]

}


