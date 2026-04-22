package com.gu.liveactivities.models

import com.gu.mobile.notifications.client.models.liveActitivites.ContentState
import play.api.libs.json._
import pa._

// BROADCAST PAYLOADS //////////////////////////////////////////////////

// Start Live Activity (via broadcast or push)
sealed trait ActivityAttributesType { val `type`: String }
case object FootballMatchAttributesType extends ActivityAttributesType {
  val `type` = "FootballMatchAttributes"
}

sealed trait ActivityAttributes
case class FootballMatchAttributes(matchId: String) extends ActivityAttributes

object ActivityAttributesJsonFormats {
  implicit val activityAttributesTypeFormat: Format[ActivityAttributesType] =
    Format(
      Reads {
        case JsString(s) =>
          s match {
            case "FootballMatchAttributes" =>
              JsSuccess(FootballMatchAttributesType)
            case other => JsError(s"Unknown match status: $other")
          }
        case _ => JsError("Expected a JSON string for ActivityAttributes")
      },
      Writes(ms => JsString(ms.`type`))
    )

  implicit val footballMatchAttributesFormat: OFormat[FootballMatchAttributes] =
    Json.format[FootballMatchAttributes]

  implicit val activityAttributesFormat: OFormat[ActivityAttributes] =
    new OFormat[ActivityAttributes] {
      def writes(a: ActivityAttributes): JsObject = a match {
        case f: FootballMatchAttributes =>
          footballMatchAttributesFormat
            .writes(f)
            .as[JsObject] + ("type" -> JsString("football"))
        // Add cases for other ActivityAttributes subtypes here
      }
      def reads(json: JsValue): JsResult[ActivityAttributes] = {
        (json \ "type").validate[String].flatMap {
          case "football" => footballMatchAttributesFormat.reads(json)
          // Add cases for other ActivityAttributes subtypes here
          case other => JsError(s"Unknown ActivityAttributes type: $other")
        }
      }
    }
}

// Update Live Activity (broadcast)
sealed trait BroadcastApsEvent {
  def timestamp: Long
  def event: String
  def `content-state`: ContentState
}
sealed trait BroadcastBody {
  def aps: BroadcastApsEvent
}

// Start Live Activity (via broadcast)
case class BroadcastStartAps(
    timestamp: Long,
    event: String = "start",
    `content-state`: ContentState,
    `attributes-type`: ActivityAttributesType,
    `attributes`: ActivityAttributes
) extends BroadcastApsEvent

case class BroadcastStartBody(
    aps: BroadcastStartAps
) extends BroadcastBody

// Update Live Activity (broadcast)
case class BroadcastUpdateAps(
    timestamp: Long,
    event: String = "update",
    `content-state`: ContentState,
    `stale-date`: Long
) extends BroadcastApsEvent

case class BroadcastUpdateBody(
    aps: BroadcastUpdateAps
) extends BroadcastBody

// End Live Activity (broadcast)
case class BroadcastEndAps(
    timestamp: Long,
    event: String = "end",
    `content-state`: ContentState,
    `dismissal-date`: Long
) extends BroadcastApsEvent

case class BroadcastEndBody(
    aps: BroadcastEndAps
) extends BroadcastBody

object BroadcastJsonFormats {
  import ActivityAttributesJsonFormats._
  import com.gu.mobile.notifications.client.models.liveActitivites.FootballContentJsonFormats._
  import ContentState.format

  implicit val broadcastStartApsFormat: OFormat[BroadcastStartAps] =
    Json.format[BroadcastStartAps]
  implicit val broadcastStartBodyFormat: OFormat[BroadcastStartBody] =
    Json.format[BroadcastStartBody]
  implicit val broadcastUpdateApsFormat: OFormat[BroadcastUpdateAps] =
    Json.format[BroadcastUpdateAps]
  implicit val broadcastUpdateBodyFormat: OFormat[BroadcastUpdateBody] =
    Json.format[BroadcastUpdateBody]
  implicit val broadcastEndApsFormat: OFormat[BroadcastEndAps] =
    Json.format[BroadcastEndAps]
  implicit val broadcastEndBodyFormat: OFormat[BroadcastEndBody] =
    Json.format[BroadcastEndBody]

  implicit val broadcastApsEventFormat: OFormat[BroadcastApsEvent] =
    new OFormat[BroadcastApsEvent] {
      def writes(e: BroadcastApsEvent): JsObject = e match {
        case s: BroadcastStartAps =>
          broadcastStartApsFormat.writes(s) + ("eventType" -> JsString("start"))
        case u: BroadcastUpdateAps =>
          broadcastUpdateApsFormat.writes(u) + ("eventType" -> JsString(
            "update"
          ))
        case e: BroadcastEndAps =>
          broadcastEndApsFormat.writes(e) + ("eventType" -> JsString("end"))
      }
      def reads(json: JsValue): JsResult[BroadcastApsEvent] =
        (json \ "eventType").validate[String].flatMap {
          case "start"  => broadcastStartApsFormat.reads(json)
          case "update" => broadcastUpdateApsFormat.reads(json)
          case "end"    => broadcastEndApsFormat.reads(json)
          case other    => JsError(s"Unknown BroadcastApsEvent type: $other")
        }
    }

  implicit val broadcastBodyFormat: OFormat[BroadcastBody] =
    new OFormat[BroadcastBody] {
      def writes(b: BroadcastBody): JsObject = b match {
        case s: BroadcastStartBody =>
          broadcastStartBodyFormat.writes(s) + ("bodyType" -> JsString("start"))
        case u: BroadcastUpdateBody =>
          broadcastUpdateBodyFormat.writes(u) + ("bodyType" -> JsString(
            "update"
          ))
        case e: BroadcastEndBody =>
          broadcastEndBodyFormat.writes(e) + ("bodyType" -> JsString("end"))
      }
      def reads(json: JsValue): JsResult[BroadcastBody] =
        (json \ "bodyType").validate[String].flatMap {
          case "start"  => broadcastStartBodyFormat.reads(json)
          case "update" => broadcastUpdateBodyFormat.reads(json)
          case "end"    => broadcastEndBodyFormat.reads(json)
          case other    => JsError(s"Unknown BroadcastBody type: $other")
        }
    }
}
