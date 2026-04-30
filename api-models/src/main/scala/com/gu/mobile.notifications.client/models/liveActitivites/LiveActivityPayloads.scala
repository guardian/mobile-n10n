package com.gu.mobile.notifications.client.models.liveActitivites

import com.gu.mobile.notifications.client.models.{Payload, Topic}
import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue, Json, Reads, Writes}

import java.util.UUID

/**
 * These are the models for eventbus payloads shared between live activity services.
 * They are emitted by the Football lambda and consumed by the Live Activity
 * Channel Manager and Broadcast lambdas.
 **/

sealed trait EventSource
case object FootballLambda extends EventSource

object EventSource {
//  implicit val reads: Reads[EventSource] = Reads {
//    case JsString("football-lambda") => JsSuccess(FootballLambda)
//    case JsString(other)             => JsError(s"Invalid EventSource: $other")
//    case _                           => JsError("EventSource must be a string")
//  }

  implicit val eventSourceFormat: Format[EventSource] = new Format[EventSource] {

    override def reads(json: JsValue): JsResult[EventSource] = json match {
      case JsString("football-lambda") => JsSuccess(FootballLambda)
      case JsString(other)             => JsError(s"Invalid EventSource: $other")
      case _                           => JsError("EventSource must be a string")
    }

    override def writes(source: EventSource): JsValue = source match {
      case FootballLambda => JsString("football-lambda")
    }
  }
}

sealed trait DetailType { def asString: String }

// TODO: look at event pusher
case object ChannelCreate   extends DetailType { val asString = "channel-create" }
case object ChannelDelete   extends DetailType { val asString = "channel-delete" }
case object BroadcastUpdate extends DetailType { val asString = "broadcast-update" }
case object BroadcastStart  extends DetailType { val asString = "broadcast-start" }
case object BroadcastEnd    extends DetailType { val asString = "broadcast-end" }

object DetailType {
  private val all: List[DetailType] = List(
    ChannelCreate,
    ChannelDelete,
    BroadcastUpdate,
    BroadcastStart,
    BroadcastEnd
  )

  private val fromString: Map[String, DetailType] =
    all.map(dt => dt.asString -> dt).toMap

  implicit val format: Format[DetailType] = new Format[DetailType] {

    override def reads(json: JsValue): JsResult[DetailType] = json match {
      case JsString(value) =>
        all.find(_.asString == value)
          .map(JsSuccess(_))
          .getOrElse(JsError(s"Invalid detail-type: $value"))

      case _ => JsError("detail-type must be a string")
    }

    override def writes(detailType: DetailType): JsValue =
      JsString(detailType.asString)
  }


//  implicit val reads: Reads[DetailType] = Reads {
//    case JsString(value) =>
//      fromString.get(value) match {
//        case Some(detailType) => JsSuccess(detailType)
//        case None             => JsError(s"Invalid detail-type: $value")
//      }
//
//    case _ =>
//      JsError("detail-type must be a string")
//  }
}

case class EventBridgeEvent(
                             version: String,
                             id: String,
                             `detail-type`: DetailType,
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

  // --- EventType ---
  implicit val liveActivityEventTypeReads: Reads[LiveActivityEventType] =
    Reads {
      case JsString("CreateChannel")     => JsSuccess(CreateChannelEvent)
      case JsString("StartLiveActivity") => JsSuccess(StartLiveActivityEvent)
      case JsString("UpdateLiveActivity") => JsSuccess(UpdateLiveActivityEvent)
      case JsString("EndLiveActivity")   => JsSuccess(EndLiveActivityEvent)
      case JsString("DeleteChannel")     => JsSuccess(DeleteChannelEvent)
      case JsString(other)               => JsError(s"Invalid LiveActivityEventType: $other")
      case _                             => JsError("LiveActivityEventType must be a string")
    }

  // --- Type ---
  implicit val liveActivityTypeReads: Reads[LiveActivityType] =
    Reads {
      case JsString("FootballLiveActivity") => JsSuccess(FootballLiveActivity)
      case JsString(other)                  => JsError(s"Invalid LiveActivityType: $other")
      case _                                => JsError("LiveActivityType must be a string")
    }

  implicit val liveActivityEventTypeFormat: Format[LiveActivityEventType] =
    Format(liveActivityEventTypeReads, liveActivityEventTypeWrites)

  implicit val liveActivityTypeFormat: Format[LiveActivityType] =
    Format(liveActivityTypeReads, liveActivityTypeWrites)

  implicit val dynamoDataWrites: Writes[dynamoData] = Json.writes[dynamoData]

  implicit val liveActivityPayloadWrites: Writes[LiveActivityPayload] =
    Json.writes[LiveActivityPayload]

  implicit val liveActivityPayloadFormat: Format[LiveActivityPayload] =
    Json.format[LiveActivityPayload]

}
