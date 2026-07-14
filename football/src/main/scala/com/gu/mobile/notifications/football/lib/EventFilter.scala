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
    distinctCheck: DynamoDistinctCheck[A, D],
    matchStateDiffer: Option[DynamoMatchStateDiffer] = None,
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
        distinctCheck.insertEvent(ev(item)).flatMap {
          case Distinct =>
            // Persist the newly broadcast content state so the next polling cycle can diff against it.
            // If the state write fails we skip emitting this event to avoid the stored state drifting
            // out of sync with what subscribers have received.
            updateMatchStateDynamoIfStateChangeEvent(item)
              .map { _ =>
                cache(item.id) // todo when to cache? before or after stateupate?
                Some(item)
              }
              .recover {
                //TODO possibility of divergence between what is is stored in the two dynamo tables for deduping.
                // We should in theory attempt another state change in a subsequent polling cycle with a unique UUID (generated using latest PA event timestamp)
                // to catch this in the next cycle.
                case NonFatal(e) =>
                  logger.error(
                    s"Failed to persist match state for ${item.liveActivityID}; skipping state-change event ${item.id}: ${e.getMessage}",
                    e,
                  )
                  None
              }
            // cases Duplicate and error
          case _ => Future.successful(None)
        }
      }
    } yield processedEvents.flatten
  }

  private def updateMatchStateDynamoIfStateChangeEvent(item: LiveActivityPayload)(implicit ec: ExecutionContext): Future[Unit] = {
    if (item.eventType == UpdateStateChangeLiveActivityEvent) {
      (item.broadcastContentStateData, matchStateDiffer) match {
        case (Some(state: FootballMatchContentState), Some(differ)) =>
          differ.updateState(item.liveActivityID, state)
          // TODO this won't be needed when this flow is duplicated for push notification handler
        case (Some(_: FootballMatchContentState), None) =>
          logger.warn(s"No match state differ configured; cannot persist state for match ${item.liveActivityID}")
          Future.unit
        case _ =>
          logger.warn(s"State-change event ${item.id} for match ${item.liveActivityID} has no football content state to persist")
          Future.unit
      }
    } else Future.unit
  }

  def filterDynamoEvents(dynamoEvents: List[A])(implicit ec: ExecutionContext): Future[List[A]] = {
    Future.traverse(dynamoEvents)(filterDynamoEvent).map(_.flatten)
  }
}
