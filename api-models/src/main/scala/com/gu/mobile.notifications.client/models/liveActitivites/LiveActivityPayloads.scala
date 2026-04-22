package com.gu.mobile.notifications.client.models.liveActitivites

import com.gu.mobile.notifications.client.models.Topic
import play.api.libs.json.{JsString, Json, Writes}
import java.util.UUID
import com.gu.mobile.notifications.client.models.Payload

/**
 * These are the models for eventbus payloads shared between live activity services.
 * They are emitted by the Football lambda and consumed by the Live Activity
 * Channel Manager and Broadcast lambdas.
 **/


// life cycle of a live activity:
sealed trait LiveActivityEventType
case object CreateChannelEvent extends LiveActivityEventType
case object StartLiveActivityEvent extends LiveActivityEventType
case object UpdateLiveActivityEvent extends LiveActivityEventType
case object EndLiveActivityEvent extends LiveActivityEventType
case object DeleteChannelEvent extends LiveActivityEventType

sealed trait LiveActivityType
case object FootballLiveActivity extends LiveActivityType
// tbc cricket, elections

case class dynamoData(
    liveActivityId: String,
    isLive: Boolean,
    data: Option[String], // tbc
    competitionId: Option[String], // should this move to data??
    lastEventId: Option[String],
    lastEventAt: Option[Long]
)

case class LiveActivityPayload(
    id: UUID, // unique event id for each payload associate with a live activity, used for de-duplication
    eventType: LiveActivityEventType,
    liveActivityType: LiveActivityType,
    liveActivityID: String, // Match ID in the case of football, tbc for other sports/events
    dynamoStoreData: Option[
      String
    ], // data not in contentstate but specific to match, election, etc TBC if this is needed.
    broadcastContentStateData: Option[ContentState],
    eventTimestamp: Long,
    topics: List[Topic] // we will need to know topics for push to start
) extends Payload

object LiveActivityPayload {
  implicit val liveActivityEventTypeWrites: Writes[LiveActivityEventType] =
    Writes {
      case CreateChannelEvent     => JsString("CreateChannel")
      case StartLiveActivityEvent  => JsString("StartLiveActivity")
      case UpdateLiveActivityEvent => JsString("UpdateLiveActivity")
      case EndLiveActivityEvent    => JsString("EndLiveActivity")
      case DeleteChannelEvent      => JsString("DeleteChannel")
    }

  implicit val liveActivityTypeWrites: Writes[LiveActivityType] = Writes {
    case FootballLiveActivity => JsString("FootballLiveActivity")
  }

  implicit val dynamoDataWrites: Writes[dynamoData] = Json.writes[dynamoData]

  implicit val liveActivityPayloadWrites: Writes[LiveActivityPayload] =
    Json.writes[LiveActivityPayload]
}
