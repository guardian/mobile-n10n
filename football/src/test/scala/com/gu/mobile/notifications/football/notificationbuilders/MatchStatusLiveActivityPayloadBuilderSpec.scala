package com.gu.mobile.notifications.football.notificationbuilders

import com.gu.mobile.notifications.client.models.DefaultGoalType
import com.gu.mobile.notifications.client.models.liveActitivites.{FirstHalf, FootballLiveActivity, FootballMatchContentState, LiveActivityPayload, TeamState, UpdateLiveActivityEvent, Competition => LACompetition}
import com.gu.mobile.notifications.football.models.Goal
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pa.{Competition, MatchDay, MatchDayTeam, Round, Stage, Venue}

import java.time.ZonedDateTime
import java.util.UUID

// todo add more tests
class MatchStatusLiveActivityPayloadBuilderSpec extends Specification {

  "A MatchStatusLiveActivityPayloadBuilder" should {

    "Build a LiveActivityPayload for a goal event" in new MatchEventsContext {
      val result = builder.build(baseGoal, matchInfo, List.empty, Some("football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live"))

      result.eventType mustEqual UpdateLiveActivityEvent
      result.liveActivityType mustEqual FootballLiveActivity
      result.liveActivityID mustEqual "some-match-id"
      result.id mustEqual UUID.nameUUIDFromBytes("football-match-status/some-match-id/".getBytes)

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
