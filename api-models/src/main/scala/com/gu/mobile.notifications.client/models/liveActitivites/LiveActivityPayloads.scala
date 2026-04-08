package com.gu.mobile.notifications.client.models.liveActitivites

import com.gu.mobile.notifications.client.models.Topic
import play.api.libs.json.{JsString, Json, Writes}
import java.util.UUID
import com.gu.mobile.notifications.client.models.Payload

///
/*
1. event type e.g. START CHANNEL, START MATCH, MATCH UPDATE, REMOVE CHANNEL
2. event ID
3. live activity type - "football" in our case
4. match ID
5. data we want to keep in the datastore
6. data we need to build the payload for broadcast messages
7. event timestamp
 */

// life cycle of a live activity:
sealed trait LiveActivityEventType
case object CreateChannel extends LiveActivityEventType
case object StartLiveActivity extends LiveActivityEventType
case object UpdateLiveActivity extends LiveActivityEventType
case object EndLiveActivity extends LiveActivityEventType
case object DeleteChannel extends LiveActivityEventType

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
//
//Column name	Description
//id	Primary key. The match ID in the football context.
//  channelId	the channel ID that has been created via Apple API for this live activity
//isChannelActive	indicate whether the channel is still active or has been closed.
//isLive	indicate whether the live activity is live i.e. the football match is taking place.
//  data	data specific to the live activity
//competitionId	competition ID of the match
//lastEventId	event ID of the last message to the live activity
//lastEventAt	timestamp of the event of the last message
//createdAt	timestamp when the record was inserted. Not directly used by the application.
//lastModifiedAt	timestamp when the record was last modified. Not directly used by the application.D

case class LiveActivityPayload(
    id: UUID,
    eventType: LiveActivityEventType,
    liveActivityType: LiveActivityType,
    liveActivityID: String, // Match ID in the case of football, tbc for other sports/events
    dynamoStoreData: Option[
      String
    ], // data not in contentstate but specific to match, election, etc TBC
    broadcastContentStateData: Option[ContentState],
    eventTimestamp: Long,
    topics: List[Topic] // we will need to know topics for push to start
) extends Payload

object LiveActivityPayload {
  implicit val liveActivityEventTypeWrites: Writes[LiveActivityEventType] =
    Writes {
      case CreateChannel      => JsString("CreateChannel")
      case StartLiveActivity  => JsString("StartLiveActivity")
      case UpdateLiveActivity => JsString("UpdateLiveActivity")
      case EndLiveActivity    => JsString("EndLiveActivity")
      case DeleteChannel      => JsString("DeleteChannel")
    }

  implicit val liveActivityTypeWrites: Writes[LiveActivityType] = Writes {
    case FootballLiveActivity => JsString("FootballLiveActivity")
  }

  implicit val dynamoDataWrites: Writes[dynamoData] = Json.writes[dynamoData]

  implicit val liveActivityPayloadWrites: Writes[LiveActivityPayload] =
    Json.writes[LiveActivityPayload]
}
