package com.gu.notifications.events.model

import java.time.temporal.{ChronoUnit, Temporal, TemporalUnit}
import java.time.{Duration, LocalDateTime}
import java.util.UUID

import play.api.libs.json.{JsValue, Json, OFormat}

import scala.collection.mutable.ListBuffer

case class EventAggregation(
  platformCounts: PlatformCount,
  providerCounts: ProviderCount,
  timing: Map[LocalDateTime, Int]
)

// based on SECONDS so adopts same properties. Else functions copied from ChronoUnit
object TenSecondUnit extends TemporalUnit {

  override def getDuration: Duration = Duration.of(10, ChronoUnit.SECONDS)

  override def isDurationEstimated: Boolean = ChronoUnit.SECONDS.isDurationEstimated

  override def isDateBased: Boolean = ChronoUnit.SECONDS.isDateBased

  override def isTimeBased: Boolean = ChronoUnit.SECONDS.isTimeBased

  override def addTo[R <: Temporal](temporal: R, amount: Long): R = temporal.plus(amount, this).asInstanceOf[R]

  override def between(temporal1Inclusive: Temporal, temporal2Exclusive: Temporal): Long = temporal1Inclusive.until(temporal2Exclusive, this)
}

object EventAggregation {


  def from(dynamoEventAggregation: DynamoEventAggregation, originalSentTime: LocalDateTime): EventAggregation = {
    val sentTime = originalSentTime.truncatedTo(TenSecondUnit)
    def timingConversion: Map[LocalDateTime, Int] = {
      val timingsBuffer = ListBuffer[(LocalDateTime, Int)]()
      dynamoEventAggregation.timing.map(timed => (timed(0), timed(1))).foldLeft(sentTime) {
        case (lastTime, (offset, count)) => {
          val nextTime = lastTime.plusSeconds(10 * offset)
          timingsBuffer += ((nextTime, count))
          nextTime
        }
      }
      timingsBuffer.toMap
    }
    EventAggregation(
      dynamoEventAggregation.platform,
      dynamoEventAggregation.provider,
      timingConversion
    )
  }

  def from(
    notificationId: UUID,
    dateTime: LocalDateTime,
    platform: Platform,
    provider: Provider
  ): EventAggregation = {
    EventAggregation(
      platformCounts = PlatformCount.from(platform),
      providerCounts = ProviderCount.from(provider, platform),
      timing = Map(dateTime -> 1)
    )
  }

  def combineTimings(timingA: Map[LocalDateTime, Int], timingB: Map[LocalDateTime, Int]): Map[LocalDateTime, Int] =
    (timingA.keySet ++ timingB.keySet).map(dateTime =>
      (timingA.get(dateTime), timingB.get(dateTime)) match {
        case (Some(a), Some(b)) => dateTime -> (a + b)
        case (Some(a), _) => dateTime -> a
        case (_, Some(b)) => dateTime -> b
        case _ => dateTime -> 0
      }
    ).toMap

  def combine(aggA: EventAggregation, aggB: EventAggregation): EventAggregation = EventAggregation(
    platformCounts = PlatformCount.combine(aggA.platformCounts, aggB.platformCounts),
    providerCounts = ProviderCount.combine(aggA.providerCounts, aggB.providerCounts),
    timing = combineTimings(aggA.timing, aggB.timing)
  )

  implicit val mapLocalDateTimeToInt = OFormat[Map[LocalDateTime, Int]](
    (value: JsValue) => value.validate[Map[String, Int]].map(_.map { case (k, v) => (LocalDateTime.parse(k), v) }),
    (dateTimeMap: Map[LocalDateTime, Int]) => Json.toJsObject(dateTimeMap.map { case (k, v) => (k.toString, v) })
  )
  implicit val jf = Json.format[EventAggregation]

}