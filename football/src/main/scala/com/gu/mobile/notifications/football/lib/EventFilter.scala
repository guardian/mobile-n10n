package com.gu.mobile.notifications.football.lib

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator
import com.gu.mobile.notifications.client.models.Payload
import com.gu.mobile.notifications.client.models.liveActitivites.{FootballMatchContentState, LiveActivityPayload, UpdateStateChangeLiveActivityEvent}
import com.gu.mobile.notifications.football.lib.DynamoDistinctCheck.{Distinct, Duplicate}
import com.gu.mobile.notifications.football.Logging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class EventFilter[A <: Payload, D](
    distinctCheck: DynamoDistinctCheck[A, D]
) extends Logging {
  private val processedEvents = new AtomicReference[Set[UUID]](Set.empty)

  private def cache(eventId: UUID): Unit = {
    processedEvents.getAndUpdate(new UnaryOperator[Set[UUID]] {
      override def apply(set: Set[UUID]): Set[UUID] = set + eventId
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
      logger.debug(s"Event ${item.id} already exists in local cache - discarding (dynamo table: ${distinctCheck.tableName})")
      Future.successful(false)
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


  // todo state change will always be here.
  private def filterOutEndEventsNotReceivedInIsolation(events: List[LiveActivityPayload]): List[LiveActivityPayload] = {
    val (endEvents, updateEvents) = events.partition(_.isEndPayload)
    val updateEventMatchIds = updateEvents.map(_.liveActivityID).toSet
    val (isolatedEndEvents, earlyEndEvents) = endEvents.partition(event => !updateEventMatchIds.contains(event.liveActivityID))

    if (earlyEndEvents.nonEmpty) logger.debug("Early end event received for match ids: " + earlyEndEvents.map(_.liveActivityID))

    isolatedEndEvents ++ updateEvents
  }

  private def filterOutStateChangeEventsNotReceivedInIsolation(events: List[LiveActivityPayload]): List[LiveActivityPayload] = {
    val (stateChangeEvents, otherEvents) = events.partition(_.eventType == com.gu.mobile.notifications.client.models.liveActitivites.UpdateStateChangeLiveActivityEvent)
    val otherEventMatchIds = otherEvents.map(_.liveActivityID).toSet
    val (isolatedStateChangeEvents, superfluousStateChangeEvents) =
      stateChangeEvents.partition(event => !otherEventMatchIds.contains(event.liveActivityID))

    // todo clean this up and change partition to filter. Hypothesis is we will have a state change event for every cycle there is an update including end.
    if (superfluousStateChangeEvents.nonEmpty)
      logger.debug("Superfluous state-change event(s) suppressed for match ids: " + superfluousStateChangeEvents.map(_.liveActivityID))

    isolatedStateChangeEvents ++ otherEvents
  }

  // we need to be able to access liveActivityId so the Payload trait must be narrowed. "An instance of A <:< B witnesses that A is a subtype of B."
  def filterDynamoEventsForLiveActivities(
      dynamoEvents: List[LiveActivityPayload],
  )(implicit ec: ExecutionContext, ev: LiveActivityPayload <:< A): Future[List[LiveActivityPayload]] = {
    for {
      newEvents <- filterAsync(dynamoEvents)(item => isUniqueRecord(ev(item)))
      eventsWithoutStateChangeEvents = filterOutStateChangeEventsNotReceivedInIsolation(newEvents)

      /** Because we poll once a minute, we might end up with a triggering update event (eg. very late goal) along with
        * an end event in the same polling cycle, but we only ever want to process the end event alone after all updates
        * have been processed (dispatched via eventbridge). This only affects Live Activities.
        */
      eventsWithoutEndEvents = filterOutEndEventsNotReceivedInIsolation(eventsWithoutStateChangeEvents)

      processedEvents <- Future.traverse(eventsWithoutEndEvents) { item =>
        distinctCheck.insertEvent(ev(item)).map {
          case Distinct => {
            cache(item.id)
            Some(item)
          }
          case _        => None
        }
      }
    } yield processedEvents.flatten
  }

  def filterDynamoEvents(dynamoEvents: List[A])(implicit ec: ExecutionContext): Future[List[A]] = {
    Future.traverse(dynamoEvents)(filterDynamoEvent).map(_.flatten)
  }
}
