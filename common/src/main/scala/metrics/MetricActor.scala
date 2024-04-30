package metrics

import org.apache.pekko.actor.Actor
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient
import com.amazonaws.services.cloudwatch.model.{MetricDatum, PutMetricDataRequest, StandardUnit, StatisticSet}
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters._
import play.api.{Environment, Mode}
import com.gu.{AppIdentity, AwsIdentity}

trait MetricActorLogic {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def cloudWatchClient: AmazonCloudWatchClient
  def stage: String
  def appName: String

  private def aggregatePointsPerMetric(metricDataPoints: List[MetricDataPoint], metricName: String): MetricDatum = {
    val (sum, min, max) = metricDataPoints.foldLeft((0d, Double.MaxValue, Double.MinValue)) { case ((aggSum, aggMin, aggMax), dataPoint) =>
      (aggSum + dataPoint.value, aggMin.min(dataPoint.value), aggMax.max(dataPoint.value))
    }

    val stats = new StatisticSet
    stats.setMaximum(max)
    stats.setMinimum(min)
    stats.setSum(sum)
    stats.setSampleCount(metricDataPoints.size.toDouble)

    val unit = metricDataPoints.headOption.map(_.unit).getOrElse(StandardUnit.None)

    val metric = new MetricDatum()
    metric.setMetricName(metricName)
    metric.setUnit(unit)
    metric.setStatisticValues(stats)

    metric
  }

  private def aggregatePointsPerNamespaceBatches(points: List[MetricDataPoint]): List[(String, List[MetricDatum])] = {
    val pointsPerMetric = points.groupBy { point => (point.namespace, point.name) }.toList
    val allAwsMetrics = pointsPerMetric.map { case ((namespace, metricName), metricPoints) =>
      namespace -> aggregatePointsPerMetric(metricPoints, metricName)
    }
    val metricsPerNamespace = allAwsMetrics.foldLeft(Map.empty[String, List[MetricDatum]]) {
      case (agg, (namespace, awsPoint)) =>
        val points = agg.getOrElse(namespace, Nil)
        agg + (namespace -> (awsPoint :: points))
    }

    metricsPerNamespace.toList.flatMap { case (namespace, awsMetrics) =>
      val awsMetricsBatches = awsMetrics.grouped(20)
      awsMetricsBatches.map { batch =>
        namespace -> batch
      }
    }
  }

  def aggregatePoints(points: List[MetricDataPoint]): Unit = {
    if (points.isEmpty) {
      logger.debug(s"No metric sent to cloudwatch.")
    } else {
      val metricsPerNamespaceBatches = aggregatePointsPerNamespaceBatches(points)

      val metricsCount = metricsPerNamespaceBatches.foldLeft(0) { case (sum, (_, batch)) => sum + batch.size }
      val batchesCount = metricsPerNamespaceBatches.size
      val namespacesCount = metricsPerNamespaceBatches.map(_._1).toSet.size

      try {
        metricsPerNamespaceBatches.foreach { case (namespace, awsMetricBatch) =>
          val metricRequest = new PutMetricDataRequest()
          metricRequest.setNamespace(s"$namespace/$stage/$appName")
          metricRequest.setMetricData(awsMetricBatch.asJava)

          cloudWatchClient.putMetricData(metricRequest)
        }
        logger.info("Sent metrics to cloudwatch. " +
          s"Data points: ${points.size}, " +
          s"Metrics: $metricsCount, " +
          s"Namespaces: $namespacesCount, " +
          s"Batches: $batchesCount")
      } catch {
        case e: Exception => logger.error(s"Unable to send metrics to cloudwatch", e)
      }
    }
  }
}

class MetricActor(val cloudWatchClient: AmazonCloudWatchClient, val identity: AppIdentity, val env: Environment) extends Actor with MetricActorLogic {
  var dataPoints = List.empty[MetricDataPoint]

  override def stage: String = identity match {
    case AwsIdentity(_, _, stage, _) => stage
    case _ => "DEV"
  }

  override def appName: String = identity match {
    case AwsIdentity(app, _, _, _) => app
    case _ => "DEV"
  }

  override def receive: Receive = {
    case metricDataPoint: MetricDataPoint if env.mode != Mode.Test =>
      dataPoints = metricDataPoint :: dataPoints
    case MetricActor.Aggregate =>
      aggregatePoints(dataPoints)
      dataPoints = Nil
  }
}

object MetricActor {
  case object Aggregate
}
