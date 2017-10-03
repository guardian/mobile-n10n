package report

import _root_.controllers.Assets
import azure.NotificationHubClient
import com.gu.AppIdentity
import com.gu.conf.{ConfigurationLoader, S3ConfigurationLocation}
import play.api.routing.Router
import play.api._
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
    val identity = AppIdentity.whoAmI(defaultAppName = "report", defaultStackName = "mobile-notifications")
    val config = ConfigurationLoader.load(identity) {
      case AppIdentity(app, stack, stage, _) => S3ConfigurationLocation (
        bucket = "mobile-notifications-dist",
        path = s"$stage/$stack/$app.conf"
      )
    }
    val loadedConfig = Configuration(config)
    val newContext = context.copy(initialConfiguration = context.initialConfiguration ++ loadedConfig )
    (new BuiltInComponentsFromContext(newContext) with AppComponents).application
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
    new DynamoNotificationReportRepository(AsyncDynamo(regions = EU_WEST_1), appConfig.dynamoReportsTableName)
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
