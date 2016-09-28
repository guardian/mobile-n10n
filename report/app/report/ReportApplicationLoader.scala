package report

import _root_.controllers.Assets
import azure.NotificationHubClient
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponents, BuiltInComponentsFromContext, LoggerConfigurator}
import play.api.ApplicationLoader.Context
import com.softwaremill.macwire._
import play.api.libs.ws.ahc.AhcWSComponents
import report.controllers.Report
import report.services.{Configuration, NotificationReportEnricher}
import router.Routes
import tracking.{DynamoNotificationReportRepository, SentNotificationReportRepository}

import scala.concurrent.ExecutionContext

class ReportApplicationLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader) foreach { _.configure(context.environment) }
    (new BuiltInComponentsFromContext(context) with AppComponents).application
  }
}

trait AppComponents extends PlayComponents
  with Controllers
  with ReportEnricher
  with AhcWSComponents
  with ReportRepository
  with AppConfiguration
  with ExecutionEnv

trait Controllers {
  self: AppConfiguration
    with ReportRepository
    with ReportEnricher
    with ExecutionEnv =>

  lazy val reportController = wire[Report]
}

trait AppConfiguration {
  lazy val appConfig = new Configuration
}

trait ReportRepository {
  self: AppConfiguration
    with ExecutionEnv =>

  import com.amazonaws.regions.Regions.EU_WEST_1
  import aws.AsyncDynamo

  lazy val notificationReportRepository: SentNotificationReportRepository =
    new DynamoNotificationReportRepository(AsyncDynamo(region = EU_WEST_1), appConfig.dynamoReportsTableName)
}

trait ReportEnricher {
  self: AppConfiguration
    with AhcWSComponents
    with ExecutionEnv =>

  lazy val defaultHubClient = new NotificationHubClient(appConfig.defaultHub, wsClient)

  lazy val reportEnricher = wire[NotificationReportEnricher]
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
}
