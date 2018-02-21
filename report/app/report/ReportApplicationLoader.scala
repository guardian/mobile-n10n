package report

import _root_.controllers.{Assets, AssetsComponents}
import akka.actor.ActorSystem
import aws.AsyncDynamo
import azure.NotificationHubClient
import com.amazonaws.regions.Regions.EU_WEST_1
import com.gu.AppIdentity
import com.gu.conf.{ConfigurationLoader, S3ConfigurationLocation}
import play.api.routing.Router
import play.api._
import play.api.ApplicationLoader.Context
import com.softwaremill.macwire._
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.EssentialFilter
import play.filters.HttpFiltersComponents
import play.filters.hosts.AllowedHostsFilter
import report.controllers.Report
import report.services.{Configuration, NotificationReportEnricher}
import router.Routes
import tracking.{DynamoNotificationReportRepository, SentNotificationReportRepository}

import scala.concurrent.ExecutionContext

class ReportApplicationLoader extends ApplicationLoader {

  def buildComponents(context: Context): BuiltInComponents = new ReportApplicationComponents(context)

  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader) foreach { _.configure(context.environment) }
    val identity = AppIdentity.whoAmI(defaultAppName = "report", defaultStackName = "mobile-notifications")
    val config = ConfigurationLoader.load(identity) {
      case AppIdentity(app, stack, stage, _) if (stage != "DEV") => S3ConfigurationLocation (
        bucket = "mobile-notifications-dist",
        path = s"$stage/$stack/$app.conf"
      )
    }
    val loadedConfig = Configuration(config)
    val newContext = context.copy(initialConfiguration = context.initialConfiguration ++ loadedConfig )
    buildComponents(newContext).application
  }
}

class ReportApplicationComponents(context: Context) extends BuiltInComponentsFromContext(context)
  with AhcWSComponents
  with HttpFiltersComponents
  with AssetsComponents {

  implicit val as: ActorSystem = actorSystem

  override def httpFilters: Seq[EssentialFilter] = super.httpFilters.filterNot{ filter => filter.getClass == classOf[AllowedHostsFilter] }

  lazy val appConfig = new Configuration
  lazy val reportController = wire[Report]

  lazy val notificationReportRepository: SentNotificationReportRepository =
    new DynamoNotificationReportRepository(AsyncDynamo(regions = EU_WEST_1), appConfig.dynamoReportsTableName)

  lazy val defaultHubClient = new NotificationHubClient(appConfig.defaultHub, wsClient)

  lazy val reportEnricher = wire[NotificationReportEnricher]
  override lazy val router: Router = wire[Routes]
  lazy val prefix: String = "/"

}


