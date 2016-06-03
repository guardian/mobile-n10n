package report

import _root_.controllers.Assets
import play.api.routing.Router
import play.api.{BuiltInComponents, BuiltInComponentsFromContext, LoggerConfigurator, Application, ApplicationLoader}
import play.api.ApplicationLoader.Context
import com.softwaremill.macwire._
import report.controllers.Report
import report.services.Configuration
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
  with ReportRepository
  with AppConfiguration
  with ExecutionEnv

trait Controllers {
  self: AppConfiguration
    with ReportRepository
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
