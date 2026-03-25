package com.gu.mobile.notifications.football.lib

import com.gu.mobile.notifications.football.Logging
import com.gu.mobile.notifications.football.models.MatchDataWithArticle
import play.api.libs.json.Json
import software.amazon.awssdk.services.eventbridge.EventBridgeClient
import software.amazon.awssdk.services.eventbridge.model.{
  PutEventsRequest,
  PutEventsRequestEntry
}

import scala.util.Try

import play.api.libs.json.{Json, Writes}
import pa._
import MatchDataWithArticleJson._
object MatchDataWithArticleJson {
  implicit val writesStage: Writes[Stage] = Json.writes[Stage]
  implicit val writesRound: Writes[Round] = Json.writes[Round]
  implicit val writesMatchDayTeam: Writes[MatchDayTeam] = Json.writes[MatchDayTeam]
  implicit val writesOfficial: Writes[Official] = Json.writes[Official]
  implicit val writesVenue: Writes[Venue] = Json.writes[Venue]
  implicit val writesPlayer: Writes[Player] = Json.writes[Player]
  implicit val writesCompetition: Writes[Competition] = Json.writes[Competition]
  implicit val writesMatchDay: Writes[MatchDay] = Json.writes[MatchDay]
  implicit val writesMatchEvent: Writes[MatchEvent] = Json.writes[MatchEvent]
  implicit val writesMatchDataWithArticle: Writes[MatchDataWithArticle] =
    Json.writes[MatchDataWithArticle]
}

class LiveActivityPusher extends Logging {

  private val eventBusName =
    "liveactivities-eventbus-CODE"
  private val eventBridgeClient =
    EventBridgeClient
      .builder()
      .build()

  def pushToEventbus(matchDataList: List[MatchDataWithArticle]) = {

    println("Try to push events to eventbus, number of events: " + matchDataList.size)
    matchDataList.map(matchData => {

      val result = Try {

        val entry = PutEventsRequestEntry
          .builder()
          .source("football-lambda")
          .detailType("football-match-events-with-articleId")
          .detail("""{"message":"Hello World"}""")
//          .detail(Json.toJson(matchData).toString())
          .eventBusName(eventBusName)
          .build()

        val request = PutEventsRequest
          .builder()
          .entries(entry)
          .build()

        val response = eventBridgeClient.putEvents(request)
        println(
          s"Event published. Failed entry count: ${response.failedEntryCount()}"
        )
      }

      result.failed.foreach(e =>
        println(s"Failed to publish event: ${e.getMessage}")
      )

    })
  }

}
