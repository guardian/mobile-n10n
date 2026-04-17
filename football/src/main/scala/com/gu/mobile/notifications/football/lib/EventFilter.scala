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
      distinctCheck.insertNotification(item).map {
        case Distinct =>
          cache(item.id)
          Some(item)
        case Duplicate =>
          cache(item.id)
          None
        case _ => None
      }
    } else {
      logger.debug(s"Event ${item.id} already exists in local cache or does not have an id - discarding")
      Future.successful(None)
    }
  }

  def filterDynamoEvents(notifications: List[A])(implicit ec: ExecutionContext): Future[List[A]] = {
    Future.traverse(notifications)(filterDynamoEvent).map(_.flatten)
  }
}
