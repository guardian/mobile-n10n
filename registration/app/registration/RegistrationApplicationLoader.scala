package registration

import _root_.controllers.AssetsComponents
import _root_.models.NewsstandShardConfig
import org.apache.pekko.actor.ActorSystem
import cats.effect.{Async, ContextShift, IO}
import com.gu.AppIdentity
import com.softwaremill.macwire._
import db.DatabaseConfig.transactorAndDataSource
import db.{DatabaseConfig, JdbcConfig, RegistrationService, RegistrationService => apply}
import doobie.Transactor
import fs2.Stream
import metrics.{CloudWatchMetrics, Metrics}
import play.api.ApplicationLoader.Context
import play.api.http.HttpErrorHandler
import play.api.inject.ApplicationLifecycle
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.api.{BuiltInComponents, BuiltInComponentsFromContext}
import play.filters.HttpFiltersComponents
import play.filters.gzip.GzipFilter
import play.filters.hosts.AllowedHostsFilter
import registration.auditor.{AuditorGroup, FootballMatchAuditor, LiveblogAuditor}
import registration.controllers.Main
import registration.services._
import registration.services.topic.{AuditorTopicValidator, TopicValidator}
import router.Routes
import utils.{CustomApplicationLoader, MobileAwsCredentialsProvider}

import scala.concurrent.{ExecutionContext, Future}

class RegistrationApplicationLoader extends CustomApplicationLoader {
  def buildComponents(identity: AppIdentity, context: Context): BuiltInComponents = new RegistrationApplicationComponents(identity, context)
}

class RegistrationApplicationComponents(identity: AppIdentity, context: Context) extends BuiltInComponentsFromContext(context)
  with AhcWSComponents
  with HttpFiltersComponents
  with AssetsComponents {

  implicit val implicitActorSystem: ActorSystem = actorSystem

  val gzipFilter = new GzipFilter()

  override def httpFilters: Seq[EssentialFilter] = super.httpFilters.filterNot { filter => filter.getClass == classOf[AllowedHostsFilter] } :+ gzipFilter

  lazy val appConfig = new Configuration(configuration)
  lazy val metrics: Metrics = new CloudWatchMetrics(applicationLifecycle, environment, identity)

  val credentialsProvider = new MobileAwsCredentialsProvider()

  lazy val auditorGroup: AuditorGroup = {
    AuditorGroup(Set(
      FootballMatchAuditor(new WSPaClient(appConfig.auditorConfiguration.paApiConfig, wsClient)),
      LiveblogAuditor(wsClient, appConfig.auditorConfiguration.contentApiConfig)
    ))
  }

  lazy val topicValidator: TopicValidator = wire[AuditorTopicValidator]
  lazy val legacyRegistrationConverter = wire[LegacyRegistrationConverter]
  lazy val legacyNewsstandRegistrationConverterConfig: NewsstandShardConfig = NewsstandShardConfig(appConfig.newsstandShards)
  lazy val legacyNewsstandRegistrationConverter: LegacyNewsstandRegistrationConverter = wire[LegacyNewsstandRegistrationConverter]

  def registrationServiceFromConfig(config: play.api.Configuration, applicationLifecycle: ApplicationLifecycle)(implicit ec: ExecutionContext): RegistrationService[IO, Stream] = {

    def transactor[F[_] : Async](config: JdbcConfig, applicationLifecycle: ApplicationLifecycle)(implicit cs: ContextShift[F]): Transactor[F] = {
      // manually creating the transactor to avoid having it wrapped in a Resource. Resources don't play well with
      // Play's way of handling lifecycle
      val (transactor, dataSource) = transactorAndDataSource(config)
      applicationLifecycle.addStopHook(() => Future.successful(dataSource.close()))
      transactor
    }

    implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)

    val masterUrl = config.get[String]("registration.db.url")
    val user = config.get[String]("registration.db.user")
    val password = config.get[String]("registration.db.password")
    val threads = config.get[Int]("registration.db.maxConnectionPoolSize")

    val masterJdbcConfig = JdbcConfig("org.postgresql.Driver", masterUrl, user, password, threads)
    val masterTransactor = transactor[IO](masterJdbcConfig, applicationLifecycle)

    RegistrationService(masterTransactor)
  }

  lazy val registrationDbService: RegistrationService[IO, fs2.Stream] = registrationServiceFromConfig(configuration, applicationLifecycle)
  lazy val databaseRegistrar: NotificationRegistrar = new DatabaseRegistrar(registrationDbService, metrics)

  lazy val mainController = new Main(databaseRegistrar, topicValidator, legacyRegistrationConverter, legacyNewsstandRegistrationConverter, appConfig, controllerComponents)

  override lazy val router: Router = wire[Routes]
  lazy val prefix: String = "/"

  override lazy val httpErrorHandler: HttpErrorHandler = new CustomErrorHandler(environment, configuration, None, Some(router))

}
