package com.gu.notifications.events.model

import org.apache.logging.log4j.LogManager
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class AggregationCounts(success: Int, failure: Int) {
  def combine(otherAttemptedCount: AggregationCounts): AggregationCounts = AggregationCounts(success + otherAttemptedCount.success, failure + otherAttemptedCount.failure)
}

object AggregationCounts {
  private val logger = LogManager.getLogger(classOf[AggregationCounts])
  implicit val jf = Json.format[AggregationCounts]

  def aggregate(triedAggregationResults: Seq[Try[AggregationCounts]]): S3ResultCounts = {
    triedAggregationResults.foldLeft(S3ResultCounts(0, 0, AggregationCounts(0, 0))) {
      case (s3Result, Success(aggregationResult)) => {
        aggregationResult.failure match {
          case 0 => s3Result.copy(success = s3Result.success + 1, aggregationResults = aggregationResult.combine(s3Result.aggregationResults))
          case _ => s3Result.copy(failure = s3Result.failure + 1, aggregationResults = aggregationResult.combine(s3Result.aggregationResults))
        }
      }
      case (s3Result, Failure(exception)) => {
        logger.warn("Error processing an s3 event", exception)
        s3Result.copy(failure = s3Result.failure + 1)
      }
    }
  }

  def aggregateResultCounts(attemptsToUpdateEachEvent: List[Future[Unit]])(implicit executionContext: ExecutionContext): Future[AggregationCounts] = {
    Future.sequence(attemptsToUpdateEachEvent.map(_.transform(t => Success(t)))).map(triedUnits => {
      triedUnits.map {
        case Success(_) => AggregationCounts(1, 0)
        case Failure(throwable) =>
          logger.warn("An attempt failed", throwable)
          AggregationCounts(0, 1)
      }.reduce((a, b) => a.combine(b))
    })
  }
}