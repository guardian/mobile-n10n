package com.gu.mobile.notifications.football.notificationbuilders

import com.gu.mobile.notifications.client.models.{DefaultGoalType, ScoredShootoutResult}
import com.gu.mobile.notifications.client.models.liveActitivites.{CreateChannelEvent, EndLiveActivityEvent, FirstHalf, FootballLiveActivity, FootballMatchContentState, LiveActivityPayload, TeamState, UpdateLiveActivityEvent, Competition => LACompetition}
import com.gu.mobile.notifications.football.models.{Abandoned, Cancelled, CreateChannel, Dismissal, EndLiveActivity, Goal, HalfTime, PenaltyShootoutKick}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pa.{Competition, MatchDay, MatchDayTeam, Round, Stage, Venue}

import java.time.ZonedDateTime
import java.util.UUID

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
      result.id mustEqual UUID.nameUUIDFromBytes("football-match-status/some-match-id/".getBytes)

      val contentState = result.broadcastContentStateData.get.asInstanceOf[FootballMatchContentState]
      contentState.competition.name mustEqual "FA Cup"
      contentState.competition.round mustEqual None
    }

    "map an Abandoned trigger to an EndLiveActivityEvent" in new MatchEventsContext {
      builder.build(Abandoned("e1"), matchInfo, List.empty, None).eventType mustEqual EndLiveActivityEvent
    }

    "map a Cancelled trigger to an EndLiveActivityEvent" in new MatchEventsContext {
      builder.build(Cancelled("e1"), matchInfo, List.empty, None).eventType mustEqual EndLiveActivityEvent
    }

    "map a CreateChannel trigger to a CreateChannelEvent" in new MatchEventsContext {
      builder.build(CreateChannel("e1"), matchInfo, List.empty, None).eventType mustEqual CreateChannelEvent
    }

    "map an EndLiveActivity trigger to an EndLiveActivityEvent" in new MatchEventsContext {
      builder.build(EndLiveActivity("e1"), matchInfo, List.empty, None).eventType mustEqual EndLiveActivityEvent
    }

    "map any other trigger (e.g. a goal) to an UpdateLiveActivityEvent" in new MatchEventsContext {
      builder.build(baseGoal, matchInfo, List.empty, None).eventType mustEqual UpdateLiveActivityEvent
    }

    "derive currentMinute from a Goal trigger" in new MatchEventsContext {
      contentStateOf(builder.build(baseGoal, matchInfo, List.empty, None)).currentMinute mustEqual Some(5)
    }

    "derive currentMinute from a Dismissal trigger" in new MatchEventsContext {
      val dismissal = Dismissal("e1", "Sent Off", home, 34, None)
      contentStateOf(builder.build(dismissal, matchInfo, List.empty, None)).currentMinute mustEqual Some(34)
    }

    "derive currentMinute from a PenaltyShootoutKick trigger" in new MatchEventsContext {
      val kick = PenaltyShootoutKick(ScoredShootoutResult, "Taker", home, away, 120, "e1")
      contentStateOf(builder.build(kick, matchInfo, List.empty, None)).currentMinute mustEqual Some(120)
    }

    "have no currentMinute for a match-phase trigger" in new MatchEventsContext {
      contentStateOf(builder.build(HalfTime("e1"), matchInfo, List.empty, None)).currentMinute mustEqual None
    }

    "aggregate goals and red cards across the triggering and previous events" in new MatchEventsContext {
      val homeGoal2 = Goal(DefaultGoalType, "Mo", home, away, 20, None, "g2")
      val awayGoal = Goal(DefaultGoalType, "Striker", away, home, 30, None, "g3")
      val homeRedCard = Dismissal("d1", "Sent Off", home, 40, None)

      val contentState = contentStateOf(builder.build(baseGoal, matchInfo, List(homeGoal2, awayGoal, homeRedCard), None))

      contentState.homeTeam.score mustEqual 2 // baseGoal + homeGoal2
      contentState.awayTeam.score mustEqual 1 // awayGoal
      contentState.homeTeam.redCards mustEqual 1
      contentState.awayTeam.redCards mustEqual 0
      contentState.homeTeam.penaltyScore mustEqual None
      contentState.awayTeam.penaltyScore mustEqual None
    }

  }

  trait MatchEventsContext extends Scope {
    val builder = new MatchStatusLiveActivityPayloadBuilder()

    def contentStateOf(payload: LiveActivityPayload): FootballMatchContentState =
      payload.broadcastContentStateData.get.asInstanceOf[FootballMatchContentState]

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
