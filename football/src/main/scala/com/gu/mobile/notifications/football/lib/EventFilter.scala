package com.gu.mobile.notifications.football.lib

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import com.gu.mobile.notifications.client.models.Payload
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

  private def isDuplicateRecord(item: A)(implicit ec: ExecutionContext): Future[Boolean] = {
    if (!processedEvents.get.contains(item.id)) {
      distinctCheck.isDuplicate(item).map { isDup =>
        if (!isDup) {
          cache(item.id)
        }
        isDup
      }
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

  def filterDynamoEventsForLiveActivities(dynamoEvents: List[A])(implicit ec: ExecutionContext): Future[List[A]] = {
    for {
      newEvents <- filterAsync(dynamoEvents)(isDuplicateRecord)
      /**
       * Because we poll once a minute, we might end up with triggering event (eg. goal) along with an end event,
       * but we only ever want to process the end event alone in a single polling cycle. This only affects Live Activities.
       */
      (endEvent, updateEvents) = newEvents.partition(_.isEndPayload)
      _ = logger.debug(s"Received ${dynamoEvents.size} events, ${newEvents.size} are new, ${endEvent.size} are end events")
      eventsToProcess = if (updateEvents.isEmpty) endEvent else updateEvents
      _ = logger.debug(s"Processing ${eventsToProcess.size} events, skipping ${newEvents.size - eventsToProcess.size} end events")

      processedEvents <- Future.traverse(eventsToProcess) { item =>
        distinctCheck.insertEvent(item).map {
          case Distinct => Some(item)
          case _ => None
        }
      }
      _ = logger.debug(s"After filtering ${processedEvents.size} events remain to be processed, ${eventsToProcess.size - processedEvents.size} were duplicates")
    } yield processedEvents.flatten
  }

  def filterDynamoEvents(dynamoEvents: List[A])(implicit ec: ExecutionContext): Future[List[A]] = {
    Future.traverse(dynamoEvents)(filterDynamoEvent).map(_.flatten)
  }
}
