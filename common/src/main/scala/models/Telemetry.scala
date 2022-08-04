package models

import models.JsonUtils._
import org.joda.time.DateTime
import play.api.libs.json.Json

sealed trait TelemetryEvent {
  def time: DateTime
  def name: String
  def message: String
}

case class PushTopicsEvent (
  time: DateTime,
  name: String,
  message: String
) extends TelemetryEvent

object PushTopicsEvent {
  implicit val jf = Json.format[PushTopicsEvent]
}
