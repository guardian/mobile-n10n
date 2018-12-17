package registration

import _root_.controllers.AssetsComponents
import akka.actor.ActorSystem
import auditor.{AuditorGroup, FootballMatchAuditor, LiveblogAuditor, TimeExpiringAuditor}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import play.api.{BuiltInComponents, BuiltInComponentsFromContext}
import play.api.ApplicationLoader.Context
import com.softwaremill.macwire._
import _root_.models.{Topic, NewsstandShardConfig}
import _root_.models.TopicTypes.ElectionResults
import org.joda.time.DateTime
import controllers.Main
import play.api.mvc.EssentialFilter
import play.filters.HttpFiltersComponents
import play.filters.hosts.AllowedHostsFilter
import registration.services.topic.{AuditorTopicValidator, TopicValidator}
import registration.services._
import utils.{CustomApplicationLoader, MobileAwsCredentialsProvider}
import router.Routes
import cats.effect.IO
import com.gu.AppIdentity
import metrics.{CloudWatchMetrics, Metrics}

class RegistrationApplicationLoader extends CustomApplicationLoader {
  def buildComponents(identity: AppIdentity, context: Context): BuiltInComponents = new RegistrationApplicationComponents(identity, context)
}

class RegistrationApplicationComponents(identity: AppIdentity, context: Context) extends BuiltInComponentsFromContext(context)
  with AhcWSComponents
  with HttpFiltersComponents
  with AssetsComponents {

  implicit val implicitActorSystem: ActorSystem = actorSystem

  override def httpFilters: Seq[EssentialFilter] = super.httpFilters.filterNot{ filter => filter.getClass == classOf[AllowedHostsFilter] }

  lazy val appConfig = new Configuration(configuration)
  lazy val metrics: Metrics = new CloudWatchMetrics(applicationLifecycle, environment, identity)

  val credentialsProvider = new MobileAwsCredentialsProvider()

  lazy val auditorGroup: AuditorGroup = {
    AuditorGroup(Set(
      FootballMatchAuditor(new WSPaClient(appConfig.auditorConfiguration.paApiConfig, wsClient)),
      LiveblogAuditor(wsClient, appConfig.auditorConfiguration.contentApiConfig),
      TimeExpiringAuditor(Set(Topic(ElectionResults, "us-presidential-2016")), DateTime.parse("2016-11-30T00:00:00Z"))
    ))
  }

  lazy val topicValidator: TopicValidator = wire[AuditorTopicValidator]
  lazy val legacyRegistrationConverter = wire[LegacyRegistrationConverter]
  lazy val legacyNewsstandRegistrationConverterConfig: NewsstandShardConfig = NewsstandShardConfig(appConfig.newsstandShards)
  lazy val legacyNewsstandRegistrationConverter: LegacyNewsstandRegistrationConverter = wire[LegacyNewsstandRegistrationConverter]

  lazy val registrationDbService: db.RegistrationService[IO, fs2.Stream] = db.RegistrationService.fromConfig(configuration, applicationLifecycle)
  lazy val databaseRegistrar: NotificationRegistrar = new DatabaseRegistrar(registrationDbService, metrics)

  lazy val mainController = new Main(databaseRegistrar, topicValidator, legacyRegistrationConverter, legacyNewsstandRegistrationConverter, appConfig, controllerComponents)

  override lazy val router: Router = wire[Routes]
  lazy val prefix: String = "/"
}