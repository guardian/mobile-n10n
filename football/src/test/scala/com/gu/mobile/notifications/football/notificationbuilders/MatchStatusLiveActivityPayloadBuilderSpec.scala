package com.gu.mobile.notifications.football.notificationbuilders

import com.gu.mobile.notifications.client.models.{DefaultGoalType, ScoredShootoutResult}
import com.gu.mobile.notifications.client.models.liveActitivites.{FirstHalf, FootballLiveActivity, FootballMatchContentState, LiveActivityPayload, PenaltyShootoutState, TeamState, UpdateLiveActivityEvent, Competition => LACompetition}
import com.gu.mobile.notifications.football.models.{Dismissal, Goal, PenaltyShootoutKick}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pa.{Competition, MatchDay, MatchDayTeam, Round, Stage, Venue}

import java.time.ZonedDateTime
import java.util.UUID

class MatchStatusLiveActivityPayloadBuilderSpec extends Specification {

  "A MatchStatusLiveActivityPayloadBuilder" should {

    "Build a LiveActivityPayload for a goal event" in new MatchEventsContext {
      val result = builder.build(baseGoal.copy(eventId = "event-id"), matchInfo, List.empty, Some("football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live"))

      result.eventType mustEqual UpdateLiveActivityEvent
      result.liveActivityType mustEqual FootballLiveActivity
      result.liveActivityID mustEqual "some-match-id"
      result.id mustEqual UUID.nameUUIDFromBytes("football-match-status/some-match-id/event-id/active".getBytes)

      val contentState = result.broadcastContentStateData.get.asInstanceOf[FootballMatchContentState]
      contentState.homeTeam.name mustEqual "Liverpool"
      contentState.homeTeam.score mustEqual 1
      contentState.awayTeam.name mustEqual "Plymouth"
      contentState.awayTeam.score mustEqual 0
      contentState.matchStatus mustEqual FirstHalf
      contentState.currentMinute mustEqual Some(5)
      contentState.competition.name mustEqual "FA Cup"
      contentState.lineupsAvailable mustEqual true
      contentState.competition.round mustEqual Some("Final")
      contentState.articleUrl mustEqual Some("http://www.theguardian.com/football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live")
      contentState.matchInfoUrl mustEqual "http://www.theguardian.com/football/match/some-match-id"
    }


    "Filter out league round name" in new MatchEventsContext {
      val result = builder.build(
        baseGoal,
        matchInfo.copy(round = Round("1", Some("League"))),
        List.empty,
        Some("football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live")
      )

      result.eventType mustEqual UpdateLiveActivityEvent
      result.liveActivityType mustEqual FootballLiveActivity
      result.liveActivityID mustEqual "some-match-id"
      result.id mustEqual UUID.nameUUIDFromBytes("football-match-status/some-match-id//active".getBytes)

      val contentState = result.broadcastContentStateData.get.asInstanceOf[FootballMatchContentState]
      contentState.competition.name mustEqual "FA Cup"
      contentState.competition.round mustEqual None
    }

    "Ignore deleted goal events" in new MatchEventsContext {
      val result = builder.build(
        baseGoal,
        matchInfo.copy(round = Round("1", Some("League"))),
        List(
          baseGoal.copy(scorerName = "Player Two", minute = 20, eventId = "event-2", isDeleted = true)
        ),
        Some("football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live")
      )
      val contentState = result.broadcastContentStateData.get.asInstanceOf[FootballMatchContentState]
      contentState.homeTeam.score mustEqual 1
      contentState.awayTeam.score mustEqual 0
    }

    "Ignore deleted dismissals events" in new MatchEventsContext {
      val result = builder.build(
        Dismissal("event-1", "Player One", home, 20, None),
        matchInfo.copy(round = Round("1", Some("League"))),
        List(
          Dismissal("event-2", "Player Two", away, 25, None, isDeleted = true)
        ),
        Some("football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live")
      )
      val contentState = result.broadcastContentStateData.get.asInstanceOf[FootballMatchContentState]
      contentState.homeTeam.redCards mustEqual 1
      contentState.awayTeam.redCards mustEqual 0
    }

    "Ignore deleted penalty events" in new MatchEventsContext {
      val result = builder.build(
        PenaltyShootoutKick(ScoredShootoutResult, "Player One", home, away, 90, "event-1"),
        matchInfo.copy(round = Round("1", Some("League"))),
        List(
          PenaltyShootoutKick(ScoredShootoutResult, "Player Two", away, home, 90, "event-2", isDeleted = true)
        ),
        Some("football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live")
      )
      val contentState = result.broadcastContentStateData.get.asInstanceOf[FootballMatchContentState]
      contentState.homeTeam.penaltyScore mustEqual Some(PenaltyShootoutState(1, 0, 0))
      contentState.awayTeam.penaltyScore mustEqual Some(PenaltyShootoutState(0, 0, 0))
    }

    "Ensure a deleted event payload has the same UUID so is not duplicated sent" in new MatchEventsContext {
      val resultActive = builder.build(
        baseGoal.copy(eventId = "event-id"),
        matchInfo.copy(round = Round("1", Some("League"))),
        List.empty,
        Some("football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live")
      )
      val resultDeleted = builder.build(
        baseGoal.copy(eventId = "event-id", isDeleted = true),
        matchInfo.copy(round = Round("1", Some("League"))),
        List.empty,
        Some("football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live")
      )

      resultActive.id mustEqual UUID.nameUUIDFromBytes("football-match-status/some-match-id/event-id/active".getBytes)
      resultDeleted.id mustEqual UUID.nameUUIDFromBytes("football-match-status/some-match-id/event-id/deleted".getBytes)
    }


  }

  trait MatchEventsContext extends Scope {
    val builder = new MatchStatusLiveActivityPayloadBuilder()
    val home = MatchDayTeam("1", "Liverpool", None, None, None, None)
    val away = MatchDayTeam("2", "Plymouth", None, None, None, None)
    val baseGoal = Goal(DefaultGoalType, "Steve", home, away, 5, None, "")
    val matchInfo = MatchDay(
      id = "some-match-id",
      date = ZonedDateTime.parse("2000-01-01T00:00:00Z"),
      competition = Some(Competition(id = "1", name = "FA Cup")),
      stage = Stage("1"),
      round = Round("1", Some("Final")),
      leg = "home",
      liveMatch = true,
      result = false,
      previewAvailable = false,
      reportAvailable = false,
      lineupsAvailable = true,
      matchStatus = "KO",
      attendance = None,
      homeTeam = home,
      awayTeam = away,
      referee = None,
      venue = Some(Venue(id = "1", name = "Wembley")),
      comments = None
    )
  }
}
