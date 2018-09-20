package com.gu.notifications.events.model

import play.api.libs.json._

case class S3ResultCounts(success: Int, failure: Int, aggregationResults: AggregationCounts)

object S3ResultCounts {
  implicit val jf = Json.format[S3ResultCounts]
}