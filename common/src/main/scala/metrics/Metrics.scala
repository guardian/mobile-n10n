package metrics

import akka.actor.{ActorSystem, Props}
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.cloudwatch.{AmazonCloudWatch, AmazonCloudWatchClientBuilder}
import play.api.Environment
import play.api.inject.ApplicationLifecycle

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

trait Metrics {
  def send(mdp: MetricDataPoint): Unit
  def executionContext: ExecutionContext
}

class CloudWatchMetrics(applicationLifecycle: ApplicationLifecycle, env: Environment, app: App) extends Metrics {

  private val actorSystem: ActorSystem = ActorSystem(
    name = "Blocking-Cloudwatch-Metrics",
    config = None,
    classLoader = Some(env.classLoader),
    defaultExecutionContext = None
  )

  applicationLifecycle.addStopHook( () => Future.successful(actorSystem.terminate()))

  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  private val cloudWatchClient: AmazonCloudWatch = {
    val region = Option(Regions.getCurrentRegion).getOrElse(Region.getRegion(Regions.EU_WEST_1)).getName
    AmazonCloudWatchClientBuilder.standard().withRegion(region).build
  }

  private val metricActor = actorSystem.actorOf(Props(classOf[MetricActor], cloudWatchClient, app, env))

  actorSystem.scheduler.schedule(
    initialDelay = 0.second,
    interval = 1.minute,
    receiver = metricActor,
    message = MetricActor.Aggregate
  )

  def send(mdp: MetricDataPoint): Unit = metricActor ! mdp
}

object DummyMetrics extends Metrics {
  def send(mdp: MetricDataPoint): Unit = {}
  def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
}
