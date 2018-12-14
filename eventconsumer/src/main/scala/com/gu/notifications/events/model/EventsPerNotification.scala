package com.gu.notifications.events.model

import java.util.UUID

case class EventsPerNotification(aggregations: Map[UUID, EventAggregation])

object EventsPerNotification {

  def from(
    notificationId: UUID,
    platform: Platform
  ): EventsPerNotification = {
    EventsPerNotification(
      Map(notificationId -> EventAggregation.from(
        platform = platform
      ))
    )
  }

  def combine(eventsA: EventsPerNotification, eventsB: EventsPerNotification): EventsPerNotification = EventsPerNotification(
    (eventsA.aggregations.keySet ++ eventsB.aggregations.keySet).map(notificationId =>
      (eventsA.aggregations.get(notificationId), eventsB.aggregations.get(notificationId)) match {
        case (Some(a), Some(b)) => notificationId -> EventAggregation.combine(a, b)
        case (Some(a), _) => notificationId -> a
        case (_, Some(b)) => notificationId -> b
        case _ => throw new RuntimeException("Inconsistent state")
      }
    ).toMap
  )

}
