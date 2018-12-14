package com.gu.notifications.events.model

import java.time.temporal.{ChronoUnit, Temporal, TemporalUnit}
import java.time.{Duration, LocalDateTime}
import java.util.UUID

import com.gu.notifications.events.utils.Percentiles
import play.api.libs.json.{JsValue, Json, OFormat, Writes}
import com.gu.notifications.events.utils.OWriteOps._

// Percentiles are durations since the first received timing
case class TimingPercentiles(
  `10th`: LocalDateTime,
  `20th`: LocalDateTime,
  `30th`: LocalDateTime,
  `40th`: LocalDateTime,
  `50th`: LocalDateTime,
  `60th`: LocalDateTime,
  `70th`: LocalDateTime,
  `80th`: LocalDateTime,
  `90th`: LocalDateTime,
  `95th`: LocalDateTime,
  `99th`: LocalDateTime
)

object TimingPercentiles {
  implicit val jf = Json.writes[TimingPercentiles]
}

case class EventAggregation(
  platformCounts: PlatformCount,
  timing: Map[LocalDateTime, Int]
) {

  def timingPercentiles: Option[TimingPercentiles] = {
    implicit val dateOrdering: Ordering[LocalDateTime] = _ compareTo _
    Seq(10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 99)
      .flatMap { p =>
        Percentiles
          .percentileBuckets(p)(timing)
          .toOption
      } match {
      case Seq(d10, d20, d30, d40, d50, d60, d70, d80, d90, d95, d99) =>
        Some(TimingPercentiles(d10, d20, d30, d40, d50, d60, d70, d80, d90, d95, d99))
      case _ => None
    }
  }

}

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
    EventAggregation(
      dynamoEventAggregation.platform,
      dynamoEventAggregation.timing.map(timed => (timed(0), timed(1))).foldLeft((sentTime, List[(LocalDateTime, Int)]())) {
        case ((lastTime, newList), (offset, count)) => {
          val nextTime = lastTime.plusSeconds(10 * offset)

          (nextTime, (nextTime, count) :: newList)
        }
      }._2.toMap,
    )
  }

  def from(
    notificationId: UUID,
    dateTime: LocalDateTime,
    platform: Platform,
  ): EventAggregation = {
    EventAggregation(
      platformCounts = PlatformCount.from(platform),
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
    timing = combineTimings(aggA.timing, aggB.timing)
  )

  implicit val mapLocalDateTimeToInt = OFormat[Map[LocalDateTime, Int]](
    (value: JsValue) => value.validate[Map[String, Int]].map(_.map { case (k, v) => (LocalDateTime.parse(k), v) }),
    (dateTimeMap: Map[LocalDateTime, Int]) => Json.toJsObject(dateTimeMap.map { case (k, v) => (k.toString, v) })
  )

  implicit val jreads = Json.reads[EventAggregation]
  implicit val jwrites: Writes[EventAggregation] =
    Json
      .writes[EventAggregation]
      .addField("timingPercentiles", _.timingPercentiles)
      .removeField("timing")

}