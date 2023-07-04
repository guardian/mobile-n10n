package com.gu.notifications.events.model

import play.api.libs.json._


case class EventAggregation(platformCounts: PlatformCount)

object EventAggregation {
  private val defaultJsonReads = Json.reads[EventAggregation]

  implicit val jreads: Reads[EventAggregation] = new Reads[EventAggregation] {
    override def reads(json: JsValue): JsResult[EventAggregation] = json match {
      case JsObject(fields) =>
        val platformCount = fields.get("platform").orElse(fields.get("platformCounts"))
        val newFields = platformCount.map(pc => Map("platformCounts" -> pc)).getOrElse(Map.empty)
        defaultJsonReads.reads(JsObject(newFields))
      case _ => JsError("Expected a JsObject to parse EventAggregation")
    }
  }
  implicit val jwrites: Writes[EventAggregation] = Json.writes[EventAggregation]
}