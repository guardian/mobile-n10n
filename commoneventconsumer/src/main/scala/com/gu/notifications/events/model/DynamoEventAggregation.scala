package com.gu.notifications.events.model

import play.api.libs.json.Json

case class DynamoEventAggregation(platform: PlatformCount)

object DynamoEventAggregation {
  implicit val jf = Json.format[DynamoEventAggregation]
}
