package com.gu.notifications.events.model

import org.apache.logging.log4j.LogManager
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class AggregationCounts(success: Int, failure: Int) {
  def combine(otherAttemptedCount: AggregationCounts): AggregationCounts = AggregationCounts(success + otherAttemptedCount.success, failure + otherAttemptedCount.failure)
}

object AggregationCounts {
  private val logger = LogManager.getLogger(classOf[AggregationCounts])
  implicit val jf = Json.format[AggregationCounts]


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