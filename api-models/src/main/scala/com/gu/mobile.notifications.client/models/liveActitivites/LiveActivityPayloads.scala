package com.gu.mobile.notifications.client.models.liveActitivites

import java.util.UUID

import play.api.libs.json._
import com.gu.mobile.notifications.client.models.Payload

/**
 * These are the models for eventbus payloads shared between live activity services.
 * They are emitted by the Football lambda and consumed by the Live Activity
 * Channel Manager and Broadcast lambdas.
 **/

sealed trait EventSource
case object FootballLambda extends EventSource

object EventSource {
  implicit val format: Format[EventSource] = new Format[EventSource] {
    def writes(source: EventSource): JsValue = source match {
      case FootballLambda => JsString("football-lambda")
    }

    def reads(json: JsValue): JsResult[EventSource] = json match {
      case JsString("football-lambda") => JsSuccess(FootballLambda)
      case JsString(other)             => JsError(s"Invalid EventSource: $other")
      case _ => JsError("EventSource must be a string")
    }
  }
}

sealed trait LiveActivityEventType {
  def asString: String
}

case object CreateChannelEvent extends LiveActivityEventType { val asString = "channel-create" }
case object StartLiveActivityEvent extends LiveActivityEventType { val asString = "broadcast-start" }
case object UpdateLiveActivityEvent extends LiveActivityEventType { val asString = "broadcast-update" }
case object EndLiveActivityEvent extends LiveActivityEventType { val asString = "broadcast-end" }
case object DeleteChannelEvent extends LiveActivityEventType { val asString = "channel-delete" }

object LiveActivityEventType {
  val values: Seq[LiveActivityEventType] = Seq(
    CreateChannelEvent,
    StartLiveActivityEvent,
    UpdateLiveActivityEvent,
    EndLiveActivityEvent,
    DeleteChannelEvent
  )

  implicit val format: Format[LiveActivityEventType] = new Format[LiveActivityEventType] {
    override def reads(json: JsValue): JsResult[LiveActivityEventType] = json match {
      case JsString("channel-create")    => JsSuccess(CreateChannelEvent)
      case JsString("broadcast-start")   => JsSuccess(StartLiveActivityEvent)
      case JsString("broadcast-update")  => JsSuccess(UpdateLiveActivityEvent)
      case JsString("broadcast-end")     => JsSuccess(EndLiveActivityEvent)
      case JsString("channel-delete")    => JsSuccess(DeleteChannelEvent)
      case JsString(other)               => JsError(s"Invalid LiveActivityEventType: $other")
      case _                             => JsError("LiveActivityEventType must be a string")
    }

    override def writes(detailType: LiveActivityEventType): JsValue =
      JsString(detailType.asString)
  }
}

case class EventBridgeEvent(
  version: String,
  id: String,
  `detail-type`: LiveActivityEventType,
  source: EventSource,
  account: String,
  time: String,
  region: String,
  resources: List[String],
  detail: LiveActivityPayload
)

object EventBridgeEvent {
  implicit val eventBridgeEventFormat: Format[EventBridgeEvent] = Json.format[EventBridgeEvent]
}


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

  implicit val liveActivityPayloadFormat: Format[LiveActivityPayload] = Json.format[LiveActivityPayload]
  implicit val dynamoDataFormat: Format[dynamoData] = Json.format[dynamoData]
}
