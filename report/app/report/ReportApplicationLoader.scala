package report

import _root_.controllers.AssetsComponents
import akka.actor.ActorSystem
import aws.AsyncDynamo
import azure.NotificationHubClient
import com.amazonaws.regions.Regions.EU_WEST_1
import play.api.routing.Router
import play.api._
import play.api.ApplicationLoader.Context
import com.softwaremill.macwire._
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.EssentialFilter
import play.filters.HttpFiltersComponents
import play.filters.hosts.AllowedHostsFilter
import report.authentication.ReportAuthAction
import report.controllers.Report
import report.services.{Configuration, NotificationReportEnricher}
import tracking.{DynamoNotificationReportRepository, SentNotificationReportRepository}
import utils.CustomApplicationLoader

import router.Routes

class ReportApplicationLoader extends CustomApplicationLoader("report") {
  def buildComponents(context: Context): BuiltInComponents = new ReportApplicationComponents(context)
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

  lazy val notificationReportRepository: SentNotificationReportRepository =
    new DynamoNotificationReportRepository(AsyncDynamo(regions = EU_WEST_1), appConfig.dynamoReportsTableName)

  lazy val defaultHubClient = new NotificationHubClient(appConfig.defaultHub, wsClient)

  lazy val reportEnricher = wire[NotificationReportEnricher]
  override lazy val router: Router = wire[Routes]
  lazy val prefix: String = "/"
}