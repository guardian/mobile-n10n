package metrics

import org.apache.pekko.actor.{ActorSystem, Props}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import play.api.Environment
import play.api.inject.ApplicationLifecycle
import utils.MobileAwsCredentialsProvider
import com.gu.AppIdentity

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Try

trait Metrics {
  def send(mdp: MetricDataPoint): Unit
  def executionContext: ExecutionContext
}

class CloudWatchMetrics(applicationLifecycle: ApplicationLifecycle, env: Environment, identity: AppIdentity) extends Metrics {

  private val actorSystem: ActorSystem = ActorSystem(
    name = "Blocking-Cloudwatch-Metrics",
    config = None,
    classLoader = Some(env.classLoader),
    defaultExecutionContext = None
  )

  applicationLifecycle.addStopHook( () => Future.successful(actorSystem.terminate()))

  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  private val cloudWatchClient: CloudWatchClient = {
    val region = Try(DefaultAwsRegionProviderChain.builder().build().getRegion).getOrElse(Region.EU_WEST_1)
    CloudWatchClient.builder()
      .region(region)
      .credentialsProvider(MobileAwsCredentialsProvider.mobileAwsCredentialsProviderv2)
      .build()
  }

  private val metricActor = actorSystem.actorOf(Props(classOf[MetricActor], cloudWatchClient, identity, env))

  actorSystem.scheduler.scheduleWithFixedDelay(
    initialDelay = 0.second,
    delay = 1.minute,
    receiver = metricActor,
    message = MetricActor.Aggregate
  )

  def send(mdp: MetricDataPoint): Unit = metricActor ! mdp
}

object DummyMetrics extends Metrics {
  def send(mdp: MetricDataPoint): Unit = {}
  def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
}
