package com.gu.mobile.notifications.football.lib

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator
import com.gu.mobile.notifications.client.models.Payload
import com.gu.mobile.notifications.client.models.liveActitivites.LiveActivityPayload
import com.gu.mobile.notifications.football.lib.DynamoDistinctCheck.{Distinct, Duplicate}
import com.gu.mobile.notifications.football.Logging

import scala.concurrent.{ExecutionContext, Future}

class EventFilter[A <: Payload, D](distinctCheck: DynamoDistinctCheck[A, D]) extends Logging {
  private val processedEvents = new AtomicReference[Set[UUID]](Set.empty)

  private def cache(eventId: UUID): Unit = {
    processedEvents.getAndUpdate(new UnaryOperator[Set[UUID]] {
      override def apply(set: Set[UUID]): Set[UUID] = set + eventId
    })
  }

  private def decache(eventId: UUID): Unit = {
    processedEvents.getAndUpdate(new UnaryOperator[Set[UUID]] {
      override def apply(set: Set[UUID]): Set[UUID] = set - eventId
    })
  }

  private def filterDynamoEvent(item: A)(implicit ec: ExecutionContext): Future[Option[A]] = {
    if (!processedEvents.get.contains(item.id)) {
      distinctCheck.insertEvent(item).map {
        case Distinct =>
          cache(item.id)
          Some(item)
        case Duplicate =>
          cache(item.id)
          None
        case _ => None
      }
    } else {
      logger.debug(s"Event ${item.id} already exists in local cache or does not have an id - discarding (dynamo table: ${distinctCheck.tableName})")
      Future.successful(None)
    }
  }

  private def isUniqueRecord(item: A)(implicit ec: ExecutionContext): Future[Boolean] = {
    if (!processedEvents.get.contains(item.id)) {
      distinctCheck.isDuplicate(item).map { isDup => !isDup }
    } else {
      logger.debug(s"Event ${item.id} already exists in local cache or does not have an id - discarding (dynamo table: ${distinctCheck.tableName})")
      Future.successful(true)
    }
  }

  private def filterAsync[A](list: List[A])(predicate: A => Future[Boolean])(implicit ec: ExecutionContext): Future[List[A]] = {
    Future.traverse(list) { item =>
      predicate(item).map {
        case true  => Some(item)
        case false => None
      }
    }.map(_.flatten)
  }


  private def filterOutEndEventsNotReceivedInIsolation(events: List[LiveActivityPayload]): List[LiveActivityPayload] = {
    val (endEvents, updateEvents) = events.partition(_.isEndPayload)
    val updateEventMatchIds = updateEvents.map(_.liveActivityID).toSet

    val (isolatedEndEvents, earlyEndEvents) = endEvents.partition(event => !updateEventMatchIds.contains(event.liveActivityID))
    isolatedEndEvents ++ updateEvents
  }

  // we need to be able to access liveActivityId so the Payload trait must be narrowed. "An instance of A <:< B witnesses that A is a subtype of B."
  def filterDynamoEventsForLiveActivities(
      dynamoEvents: List[LiveActivityPayload],
  )(implicit ec: ExecutionContext, ev: LiveActivityPayload <:< A): Future[List[LiveActivityPayload]] = {
    for {
      newEvents <- filterAsync(dynamoEvents)(item => isUniqueRecord(ev(item)))
      /** Because we poll once a minute, we might end up with a triggering update event (eg. very late goal) along with
        * an end event in the same polling cycle, but we only ever want to process the end event alone after all updates
        * have been processed (dispatched via eventbridge). This only affects Live Activities.
        */
      eventsToProcess = filterOutEndEventsNotReceivedInIsolation(newEvents)
      processedEvents <- Future.traverse(eventsToProcess) { item =>
        distinctCheck.insertEvent(ev(item)).map {
          case Distinct => {
            cache(item.id)
            Some(item)
          }
          case _        => None
        }
      }
      _ = logger.debug(
        s"${dynamoEvents.size} events to filter. ${newEvents.size} were new and not duplicates in (dynamo table: ${distinctCheck.tableName}). " +
          s"${newEvents.size - eventsToProcess.size} were early end events, leaving ${eventsToProcess.size} events to process this cycle",
      )
    } yield processedEvents.flatten
  }

  def filterDynamoEvents(dynamoEvents: List[A])(implicit ec: ExecutionContext): Future[List[A]] = {
    Future.traverse(dynamoEvents)(filterDynamoEvent).map(_.flatten)
  }
}
