package com.gu.mobile.notifications.client.models.liveActitivites

import java.util.UUID

import play.api.libs.json._
import com.gu.mobile.notifications.client.models.Payload

/**
 * These are the models for eventbus payloads shared between live activity services.
 * They are emitted by the Football lambda and consumed by the Live Activity
 * Channel Manager and Broadcast lambdas.
 **/

case class EventBridgeEvent(
  version: String,
  id: String,
  `detail-type`: String,
  source: String,
  account: String,
  time: String,
  region: String,
  resources: List[String],
  detail: LiveActivityPayload
)

object EventBridgeEvent {
  implicit val eventBridgeEventFormat: Format[EventBridgeEvent] = Json.format[EventBridgeEvent]
}

// todo codify detail-type strings


// Life cycle of a live activity:
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
  competitionId: Option[String], // should this move to data??†
  lastEventId: Option[String],
  lastEventAt: Option[Long]
)

case class LiveActivityPayload(
  id: UUID, // unique event id for each payload associate with a live activity, used for de-duplication
  eventType: LiveActivityEventType,
  liveActivityType: LiveActivityType,
  liveActivityID: String, // Match ID in the case of football, tbc for other sports/events
  dynamoStoreData: Option[String], // data not in contentstate but specific to match, election, etc TBC if this is needed.
  broadcastContentStateData: Option[ContentState],
  eventTimestamp: Long,
//  topics: List[Topic] // we will need to know topics for push to start
) extends Payload

object LiveActivityPayload {

  implicit val liveActivityTypeFormat: Format[LiveActivityType] = Format(
    Reads {
      case JsString("FootballLiveActivity") => JsSuccess(FootballLiveActivity)
      case _                                => JsError("Unknown LiveActivityType")
    },
    Writes {
      case FootballLiveActivity => JsString("FootballLiveActivity")
    }
  )

  implicit val liveActivityEventTypeFormat: Format[LiveActivityEventType] = Format(
    Reads {
      case JsString("CreateChannel")      => JsSuccess(CreateChannelEvent)
      case JsString("StartLiveActivity")  => JsSuccess(StartLiveActivityEvent)
      case JsString("UpdateLiveActivity") => JsSuccess(UpdateLiveActivityEvent)
      case JsString("EndLiveActivity")    => JsSuccess(EndLiveActivityEvent)
      case JsString("DeleteChannel")      => JsSuccess(DeleteChannelEvent)
      case _                              => JsError("Unknown LiveActivityEventType")
    },
    Writes {
      case CreateChannelEvent      => JsString("CreateChannel")
      case StartLiveActivityEvent  => JsString("StartLiveActivity")
      case UpdateLiveActivityEvent => JsString("UpdateLiveActivity")
      case EndLiveActivityEvent    => JsString("EndLiveActivity")
      case DeleteChannelEvent      => JsString("DeleteChannel")
    }
  )

  implicit val liveActivityPayloadFormat: Format[LiveActivityPayload] = Json.format[LiveActivityPayload]
  implicit val dynamoDataFormat: Format[dynamoData] = Json.format[dynamoData]
}
