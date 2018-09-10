package com.gu.notifications.events.model

import java.time.LocalDateTime

import play.api.libs.json.Json

case class DynamoEventAggregation
(
  platform: PlatformCount,
  provider: ProviderCount,
  timing: List[List[Int]]
)
object DynamoEventAggregation {
  implicit val jf = Json.format[DynamoEventAggregation]
}
