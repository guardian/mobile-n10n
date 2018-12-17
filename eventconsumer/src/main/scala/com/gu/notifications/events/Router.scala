package com.gu.notifications.events

import java.util.concurrent.TimeUnit

import com.gu.notifications.events.dynamo.DynamoReportUpdater
import com.gu.notifications.events.model.{AggregationCounts, NotificationReportEvent, S3ResultCounts}
import com.gu.notifications.events.s3.{S3Event, S3EventProcessor}
import com.gu.notifications.events.sqs.SqsEventReader
import org.apache.logging.log4j.{LogManager, Logger}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class Router(eventConsumer: S3EventProcessor, reportUpdater: DynamoReportUpdater)(implicit executionContext: ExecutionContext) {
  private val logger: Logger = LogManager.getLogger(classOf[Router])

  def sqsEventRoute(inputString: String): Unit = {
    val (s3Events, s3EventFailures) = SqsEventReader.readSqsEventString(inputString).foldLeft((List.empty[S3Event], List.empty[Throwable])) {
      case ((lastSuccesses, lastFailures), attempt) => attempt match {
        case Success(value) => (value :: lastSuccesses, lastFailures)
        case Failure(exception) => (lastSuccesses, exception :: lastFailures)
      }
    }
    val s3EventAggregateProviders = s3Events.map(s3Event => () => s3EventRoute(s3Event))
    val eventualTriedNotificationCounts = callProvidersInSeries(s3EventAggregateProviders)
    val triedNotificationCounts = Await.result(eventualTriedNotificationCounts, Duration(4, TimeUnit.MINUTES))
    val s3ResultCounts: S3ResultCounts = AggregationCounts.aggregate(triedNotificationCounts)
    logger.info(S3ResultCounts.jf.writes(s3ResultCounts).toString())
    if (s3ResultCounts.failure > 0 || s3EventFailures.nonEmpty) {
      throw new Exception("Error happened")
    }
  }

  def s3EventRoute(s3EventJson: S3Event): Future[AggregationCounts] = {
    val eventsPerNotification = eventConsumer.s3EventsToEventsPerNotification(s3EventJson)
    val attemptsToUpdateEachReport = reportUpdater.update(eventsPerNotification.aggregations.map {
      case (id, eventAggregation) => NotificationReportEvent(id.toString, eventAggregation)
    }.toList)
    AggregationCounts.aggregateResultCounts(attemptsToUpdateEachReport)
  }

  private def callProvidersInSeries[T](providers: Seq[() => Future[T]]): Future[Seq[Try[T]]] = {
    providers.foldLeft(Future.successful(Seq.empty[Try[T]])) {
      case (eventualAttemptsSoFar, provider) => eventualAttemptsSoFar.flatMap(attemptsSoFar => provider().transformWith(attempt => Future.successful(attemptsSoFar :+ attempt)))
    }
  }

}
