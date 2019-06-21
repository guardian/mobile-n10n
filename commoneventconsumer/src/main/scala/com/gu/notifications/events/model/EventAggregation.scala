package com.gu.notifications.events.model

import play.api.libs.json.{Json, Writes}


case class EventAggregation(platformCounts: PlatformCount)

object EventAggregation {

  def from(dynamoEventAggregation: DynamoEventAggregation): EventAggregation = EventAggregation(dynamoEventAggregation.platform)

  def from(
    platform: Platform,
  ): EventAggregation = {
    EventAggregation(
      platformCounts = PlatformCount.from(platform)
    )
  }

  def combine(aggA: EventAggregation, aggB: EventAggregation): EventAggregation = EventAggregation(
    platformCounts = PlatformCount.combine(aggA.platformCounts, aggB.platformCounts)
  )

  implicit val jreads = Json.reads[EventAggregation]
  implicit val jwrites: Writes[EventAggregation] = Json.writes[EventAggregation]
}