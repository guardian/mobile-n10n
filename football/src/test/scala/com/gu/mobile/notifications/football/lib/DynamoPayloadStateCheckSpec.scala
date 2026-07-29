package com.gu.mobile.notifications.football.lib

import com.gu.mobile.notifications.client.models.liveActitivites._
import org.specs2.mutable.Specification
import play.api.libs.json.Json

import java.util.UUID

class DynamoPayloadStateCheckSpec extends Specification {

  val stateChecker = new DynamoPayloadStateCheck(null, "test-table")

  def footballState(
    homeScore: Int = 0,
    awayScore: Int = 0,
    homeRedCards: Int = 0,
    awayRedCards: Int = 0,
    matchStatus: MatchStatus = FirstHalf,
    articleUrl: Option[String] = None,
  ): FootballMatchContentState =
    FootballMatchContentState(
      matchStatus = matchStatus,
      kickOffTimestamp = 0L,
      homeTeam = TeamState(id = "1", name = "Arsenal", score = homeScore, redCards = homeRedCards),
      awayTeam = TeamState(id = "2", name = "Chelsea", score = awayScore, redCards = awayRedCards),
      competition = Competition(id = "100", name = "Premier League"),
      articleUrl = articleUrl,
      matchInfoUrl = "https://www.theguardian.com/football/match/1",
    )

  // Build the `payload` column exactly as production does: the JSON-serialised LiveActivityPayload.
  def payloadJson(state: Option[ContentState], eventType: LiveActivityEventType = UpdateLiveActivityEvent): String = {
    val payload = LiveActivityPayload(
      id = UUID.randomUUID(),
      eventType = eventType,
      liveActivityType = FootballLiveActivity,
      liveActivityID = "match-1",
      dynamoStoreData = None,
      broadcastContentStateData = state,
      eventTimestamp = 0L,
    )
    Json.prettyPrint(Json.toJson(payload))
  }

  def row(state: Option[ContentState], ttl: Long): DynamoMatchLiveActivity =
    DynamoMatchLiveActivity(
      id = UUID.randomUUID().toString,
      liveActivityID = "match-1",
      payload = payloadJson(state),
      ttl = ttl,
    )

  "footballStateFromPayload" should {
    "extract the football content state from a serialised payload (round-trips through JSON)" in {
      val state = footballState(homeScore = 1)
      stateChecker.footballStateFromPayload(payloadJson(Some(state))) must beSome(state)
    }

    "return None when the payload carries no broadcast content state" in {
      stateChecker.footballStateFromPayload(payloadJson(None, eventType = CreateChannelEvent)) must beNone
    }
  }

  "isMatchStateIdentical(isIdenticalToLatest) decision logic " should {
    "assume identical when there is no previously stored payload for the match (first match payload)" in {
      stateChecker.isIdenticalToLatest(Nil, footballState()) must beTrue
    }

    "return true when the latest payload state equals the provided state" in {
      val state = footballState(homeScore = 1, homeRedCards = 1)
      stateChecker.isIdenticalToLatest(List(row(Some(state), ttl = 10)), state) must beTrue
    }

    "return false when the latest payload state differs (e.g. a new goal)" in {
      val latest = footballState(homeScore = 1)
      val current = footballState(homeScore = 2)
      stateChecker.isIdenticalToLatest(List(row(Some(latest), ttl = 10)), current) must beFalse
    }

    "ignore articleUrl differences (treated as identical)" in {
      val stored = footballState(homeScore = 1, articleUrl = None)
      val current = footballState(homeScore = 1, articleUrl = Some("https://www.theguardian.com/football/article"))
      stateChecker.isIdenticalToLatest(List(row(Some(stored), ttl = 10)), current) must beTrue
    }

    "still detect a real change when articleUrl also differs" in {
      val stored = footballState(homeScore = 1, articleUrl = None)
      val current = footballState(homeScore = 2, articleUrl = Some("https://www.theguardian.com/football/article"))
      stateChecker.isIdenticalToLatest(List(row(Some(stored), ttl = 10)), current) must beFalse
    }
  }
}
