package com.gu.notifications.events

import java.time.{Duration, LocalDateTime}
import java.time.temporal.{ChronoUnit, Temporal, TemporalUnit}
import java.util.UUID

import scala.collection.SortedMap

case class PlatformCount(
  total: Int,
  ios: Int,
  android: Int
)

object PlatformCount {
  def from(platform: Platform): PlatformCount = platform match {
    case Ios => PlatformCount(1, 1, 0)
    case Android => PlatformCount(1, 0, 1)
  }

  def combine(countsA: PlatformCount, countsB: PlatformCount): PlatformCount = PlatformCount(
    total = countsA.total + countsB.total,
    ios = countsA.ios + countsB.ios,
    android = countsA.android + countsB.android
  )

}

case class EventAggregation(
  notificationId: UUID,
  counts: PlatformCount,
  timing: Map[LocalDateTime, Int]
)

object EventAggregation {
  def from(
    notificationId: UUID,
    dateTime: LocalDateTime,
    platform: Platform,
    provider: Provider
  ): EventAggregation = {
    EventAggregation(
      notificationId = notificationId,
      counts = PlatformCount.from(platform),
      timing = Map(dateTime -> 1)
    )
  }

  def combineTimings(timingA: Map[LocalDateTime, Int], timingB: Map[LocalDateTime, Int]): Map[LocalDateTime, Int] = {
    val keys = timingA.keySet ++ timingB.keySet
    keys.map( dateTime =>
      (timingA.get(dateTime), timingB.get(dateTime)) match {
        case (Some(a), Some(b)) => dateTime -> (a + b)
        case (Some(a), _) => dateTime -> a
        case (_, Some(b)) => dateTime -> b
        case _ => dateTime -> 0
      }
    ).toMap
  }

  def combine(aggA: EventAggregation, aggB: EventAggregation): EventAggregation = EventAggregation(
    notificationId = aggA.notificationId,
    counts = PlatformCount.combine(aggA.counts, aggB.counts),
    timing = combineTimings(aggA.timing, aggB.timing)
  )

}

case class EventsPerNotification(aggregations: Map[UUID, EventAggregation])

object EventsPerNotification {
  val tenSeconds = new TemporalUnit() {
    private val duration = Duration.ofSeconds(10L)
    override def isDurationEstimated: Boolean = false
    override def getDuration: Duration = duration
    override def isTimeBased: Boolean = true
    override def addTo[R <: Temporal](r: R, l: Long): R = r.plus(10, ChronoUnit.SECONDS).asInstanceOf[R]
    override def isDateBased: Boolean = true
    override def between(temporal: Temporal, temporal1: Temporal): Long = temporal.until(temporal1, ChronoUnit.SECONDS) / 10
  }
  def from(
    notificationId: UUID,
    dateTime: LocalDateTime,
    platform: Platform,
    provider: Provider
  ): EventsPerNotification = {
    EventsPerNotification(
      Map(notificationId -> EventAggregation.from(
        notificationId = notificationId,
        dateTime = dateTime.truncatedTo(tenSeconds),
        platform = platform,
        provider = provider
      ))
    )
  }

  def combine(eventsA: EventsPerNotification, eventsB: EventsPerNotification): EventsPerNotification = {
    val keys = eventsA.aggregations.keySet ++ eventsB.aggregations.keySet
    val combined = keys.map ( notificationId =>
      (eventsA.aggregations.get(notificationId), eventsB.aggregations.get(notificationId)) match {
        case (Some(a), Some(b)) => notificationId -> EventAggregation.combine(a, b)
        case (Some(a), _) => notificationId -> a
        case (_, Some(b)) => notificationId -> b
        case _ => throw new RuntimeException("Inconsistent state")
      }
    ).toMap
    EventsPerNotification(combined)
  }
}
