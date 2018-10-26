package report

import _root_.controllers.AssetsComponents
import akka.actor.ActorSystem
import aws.AsyncDynamo
import azure.NotificationHubClient
import cats.effect.IO
import com.amazonaws.regions.Regions.EU_WEST_1
import com.gu.AppIdentity
import play.api.routing.Router
import play.api._
import play.api.ApplicationLoader.Context
import com.softwaremill.macwire._
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.EssentialFilter
import play.filters.HttpFiltersComponents
import play.filters.hosts.AllowedHostsFilter
import report.authentication.ReportAuthAction
import report.controllers.{RegistrationCount, Report}
import report.services.{Configuration, NotificationReportEnricher}
import tracking.{DynamoNotificationReportRepository, SentNotificationReportRepository}
import utils.{CustomApplicationLoader, MobileAwsCredentialsProvider}
import router.Routes

class ReportApplicationLoader extends CustomApplicationLoader {
  def buildComponents(identity: AppIdentity, context: Context): BuiltInComponents = new ReportApplicationComponents(context)
}

class ReportApplicationComponents(context: Context) extends BuiltInComponentsFromContext(context)
  with AhcWSComponents
  with HttpFiltersComponents
  with AssetsComponents {

  implicit val as: ActorSystem = actorSystem

  override def httpFilters: Seq[EssentialFilter] = super.httpFilters.filterNot{ filter => filter.getClass == classOf[AllowedHostsFilter] }

  lazy val appConfig = new Configuration(configuration)
  lazy val authAction = wire[ReportAuthAction]

  lazy val reportController = wire[Report]

  val credentialsProvider = new MobileAwsCredentialsProvider()

  lazy val notificationReportRepository: SentNotificationReportRepository =
    new DynamoNotificationReportRepository(AsyncDynamo(regions = EU_WEST_1, credentialsProvider), appConfig.dynamoReportsTableName)

  lazy val defaultHubClient = new NotificationHubClient(appConfig.defaultHub, wsClient)

  lazy val registrationDbService: db.RegistrationService[IO, fs2.Stream] = db.RegistrationService.fromConfig(configuration, applicationLifecycle)

  lazy val registrationCount: RegistrationCount = wire[RegistrationCount]

  lazy val reportEnricher = wire[NotificationReportEnricher]
  override lazy val router: Router = wire[Routes]
  lazy val prefix: String = "/"
}