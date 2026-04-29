package com.gu.liveactivities.models

import com.gu.mobile.notifications.client.models.liveActitivites._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json._

class BroadcastPayloadsSpec extends Specification {

  trait BroadcastScope extends Scope {
    val now = System.currentTimeMillis() / 1000

    val homeTeam = TeamState(name = "Arsenal", score = 1)
    val awayTeam = TeamState(name = "Chelsea", score = 0)
    val competition = Competition(name = "Premier League", round = Some("League"))

    val contentState = FootballMatchContentState(
      matchStatus = FirstHalf,
      kickOffTimestamp = now - 1800, // 30min ago
      homeTeam = homeTeam,
      awayTeam = awayTeam,
      competition = competition,
      commentary = Some("Arsenal dominating."),
      currentMinute = Some(30)
    )

    val eventTime = now - 60L // 1min ago
  }

  "BroadcastBody.apply" should {

    "produce a BroadcastUpdateAps with stale date when shouldEndBroadcast is false" in new BroadcastScope {
      val body = BroadcastBody(contentState, shouldEndBroadcast = false, eventTime)
      body.aps must beAnInstanceOf[BroadcastUpdateAps]
      body.aps.event mustEqual "update"
      val updateAps = body.aps.asInstanceOf[BroadcastUpdateAps]
      updateAps.`stale-date` must be_>=(now + 2750L)
      updateAps.`stale-date` must be_<=(now + 2770L)
    }

    "produce a BroadcastEndAps with dimissal date when shouldEndBroadcast is true" in new BroadcastScope {
      val body = BroadcastBody(contentState, shouldEndBroadcast = true, eventTime)
      body.aps must beAnInstanceOf[BroadcastEndAps]
      body.aps.event mustEqual "end"
      val endAps = body.aps.asInstanceOf[BroadcastEndAps]
      endAps.`dismissal-date` must be_>=(now + 3590L)
      endAps.`dismissal-date` must be_<=(now + 3610L)
    }

    "set the content-state correctly" in new BroadcastScope {
      val body = BroadcastBody(contentState, shouldEndBroadcast = false, eventTime)
      body.aps.`content-state` mustEqual contentState
    }
  }

  "BroadcastBody JSON serialisation" should {

    "serialise an update body and deserialise back to the same value" in new BroadcastScope {
      import BroadcastApsJsonFormats._

      val body = BroadcastBody(contentState, shouldEndBroadcast = false, eventTime)
      val json = Json.toJson(body)
      val parsed = json.validate[BroadcastBody]

      parsed must beLike {
        case JsSuccess(result, _) =>
          result.aps.event mustEqual "update"
          result.aps.`content-state` mustEqual contentState
      }
    }

    "serialise an end body and deserialise back to the same value" in new BroadcastScope {
      import BroadcastApsJsonFormats._

      val body = BroadcastBody(contentState, shouldEndBroadcast = true, eventTime)
      val json = Json.toJson(body)
      val parsed = json.validate[BroadcastBody]

      parsed must beLike {
        case JsSuccess(result, _) =>
          result.aps.event mustEqual "end"
          result.aps.`content-state` mustEqual contentState
      }
    }

    "include an 'event' field with value 'update' in the serialised JSON" in new BroadcastScope {
      import BroadcastApsJsonFormats._

      val body = BroadcastBody(contentState, shouldEndBroadcast = false, eventTime)
      val json = Json.toJson(body)
      (json \ "aps" \ "event").as[String] mustEqual "update"
    }

    "include an 'event' field with value 'end' in the serialised JSON" in new BroadcastScope {
      import BroadcastApsJsonFormats._

      val body = BroadcastBody(contentState, shouldEndBroadcast = true, eventTime)
      val json = Json.toJson(body)
      (json \ "aps" \ "event").as[String] mustEqual "end"
    }
  }
}

