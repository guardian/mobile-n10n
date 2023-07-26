package com.gu.notifications.events.model

import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class AggregationCounts(success: Int, failure: Int) {
  def combine(otherAttemptedCount: AggregationCounts): AggregationCounts = AggregationCounts(success + otherAttemptedCount.success, failure + otherAttemptedCount.failure)
}

object AggregationCounts {
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  implicit val jf: OFormat[AggregationCounts] = Json.format[AggregationCounts]


  def aggregateResultCounts(attemptsToUpdateEachEvent: List[Future[Unit]])(implicit executionContext: ExecutionContext): Future[AggregationCounts] = {
    Future.sequence(attemptsToUpdateEachEvent.map(_.transform(t => Success(t)))).map(triedUnits => {
      val listOfCounts = triedUnits.map {
        case Success(_) => AggregationCounts(1, 0)
        case Failure(throwable) =>
          logger.warn("An attempt failed", throwable)
          AggregationCounts(0, 1)
      }
      if (listOfCounts.isEmpty) {
        AggregationCounts(0, 0)
      }
      else {
        listOfCounts.reduce((a, b) => a.combine(b))
      }
    })
  }
}