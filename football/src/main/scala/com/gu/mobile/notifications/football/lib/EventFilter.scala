package com.gu.mobile.notifications.football.lib

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import com.gu.mobile.notifications.client.models.NotificationPayload
import com.gu.mobile.notifications.football.lib.DynamoDistinctCheck.{Distinct, Duplicate}
import com.gu.mobile.notifications.football.Logging

import scala.concurrent.{ExecutionContext, Future}

class EventFilter(distinctCheck: DynamoDistinctCheck) extends Logging {
  private val processedEvents = new AtomicReference[Set[UUID]](Set.empty)

  private def cache(eventId: UUID): Unit = {
    processedEvents.getAndUpdate(new UnaryOperator[Set[UUID]] {
      override def apply(set: Set[UUID]): Set[UUID] = set + eventId
    })
  }

  private def filterNotification(notification: NotificationPayload)(implicit ec: ExecutionContext): Future[Option[NotificationPayload]] = {
    if (!processedEvents.get.contains(notification.id)) {
      distinctCheck.insertNotification(notification).map {
        case Distinct =>
          cache(notification.id)
          Some(notification)
        case Duplicate =>
          cache(notification.id)
          None
        case _ => None
      }
    } else {
      logger.debug(s"Event ${notification.id} already exists in local cache or does not have an id - discarding")
      Future.successful(None)
    }
  }

  def filterNotifications(notifications: List[NotificationPayload])(implicit ec: ExecutionContext): Future[List[NotificationPayload]] = {
    Future.traverse(notifications)(filterNotification).map(_.flatten)
  }
}
