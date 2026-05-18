package com.gu.mobile.notifications.football.notificationbuilders

import java.net.URI
import java.time.ZonedDateTime
import java.util.UUID

import com.gu.mobile.notifications.client.models.Importance.{Major, Minor}
import com.gu.mobile.notifications.client.models._
import com.gu.mobile.notifications.football.lib.SyntheticMatchEventGenerator
import com.gu.mobile.notifications.football.models.{Dismissal, FootballMatchEvent, Goal, GoalContext, KickOff, PenaltyShootoutKick, Score}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pa.{Competition, MatchDay, MatchDayTeam, Parser, Round, Stage, Venue}

import java.time.ZonedDateTime
import scala.io.Source


class MatchStatusNotificationBuilderSpec extends Specification {

  "A MatchStatusNotificationBuilder" should {

    "Build a notification" in new MatchEventsContext {
      val notification = builder.build(baseGoal, matchInfo, List.empty, Some("football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live"))
      notification shouldEqual FootballMatchStatusPayload(
        title = Some("Goal!"),
        message = Some("Liverpool 1-0 Plymouth (1st)\nSteve 5min"),
        thumbnailUrl = None,
        sender = "mobile-notifications-football-lambda",
        awayTeamName = "Plymouth",
        awayTeamId = "2",
        awayTeamScore = 0,
        awayTeamMessage = " ",
        awayTeamRedCards = 0,
        homeTeamName = "Liverpool",
        homeTeamId = "1",
        homeTeamScore = 1,
        homeTeamMessage = "Steve 5'",
        homeTeamRedCards = 0,
        competitionName = Some("FA Cup"),
        roundName = None,
        venue = Some("Wembley"),
        matchId = "some-match-id",
        matchInfoUri = new URI("http://localhost/sport/football/matches/some-match-id"),
        articleUri = Some(new URI("http://localhost/items/football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live")),
        importance = Major,
        topic = List(Topic(TopicTypes.FootballTeam, "1"), Topic(TopicTypes.FootballTeam, "2"), Topic(TopicTypes.FootballMatch, "some-match-id")),
        eventId = UUID.nameUUIDFromBytes("".getBytes).toString,
        matchStatus = "1st",
        kickOffTimestamp = Some(ZonedDateTime.parse("2000-01-01T00:00:00Z").toEpochSecond),
        lineupsAvailable = Some(false),
        detailedMatchStatus = Some("FIRST_HALF"),
        debug = false,
        dryRun = None
      )
    }

    "Include detailedMatchStatus for kick off" in new MatchEventsContext {
      val kickOff = KickOff("")
      val notification = builder.build(kickOff, matchInfo.copy(matchStatus = "Fixture"), List.empty, None)
      notification.detailedMatchStatus shouldEqual Some("FIRST_HALF")
    }

    "Include detailedMatchStatus for penalties" in new MatchEventsContext {
      val matchInPenalties = matchInfo.copy(matchStatus = "PT")
      val notification = builder.build(baseGoal, matchInPenalties, List.empty, None)
      notification.detailedMatchStatus shouldEqual Some("PENALTIES")
    }

    "Include detailedMatchStatus for extra time half time" in new MatchEventsContext {
      val matchInETHT = matchInfo.copy(matchStatus = "ETHT")
      val notification = builder.build(baseGoal, matchInETHT, List.empty, None)
      notification.detailedMatchStatus shouldEqual Some("EXTRA_TIME_HALF_TIME")
    }

    "Include lineupsAvailable true from matchInfo" in new MatchEventsContext {
      val matchInfoWithLineups = matchInfo.copy(lineupsAvailable = true)
      val notification = builder.build(baseGoal, matchInfoWithLineups, List.empty, None)
      notification.lineupsAvailable shouldEqual Some(true)
    }

    "Include lineupsAvailable false from matchInfo if not available" in new MatchEventsContext {
      val notification = builder.build(baseGoal, matchInfo, List.empty, None)
      notification.lineupsAvailable shouldEqual Some(false)
    }

    "Include kickOffTimestamp from match info" in new MatchEventsContext {
      val notification = builder.build(baseGoal, matchInfo, List.empty, None)
      notification.kickOffTimestamp shouldEqual Some(matchInfo.date.toEpochSecond)
    }

    "Include the correct red cards count for each team" in new MatchEventsContext {
      val firstDismissal  = Dismissal("e1", "Player A", home, 55, None)
      val secondDismissal = Dismissal("e2", "Player B", home, 80, None)
      val notification = builder.build(secondDismissal, matchInfo, List(firstDismissal), None)
      notification.homeTeamRedCards shouldEqual 2
      notification.awayTeamRedCards shouldEqual 0
    }
  }

  trait MatchEventsContext extends Scope {
    val goalTypes = List(OwnGoalType, PenaltyGoalType, DefaultGoalType)
    val builder = new MatchStatusNotificationBuilder("http://localhost")
    val home = MatchDayTeam("1", "Liverpool", None, None, None, None)
    val away = MatchDayTeam("2", "Plymouth", None, None, None, None)
    val baseGoal = Goal(DefaultGoalType, "Steve", home, away, 5, None, "")
    val goalContext = GoalContext(home, away, "match-1", Score(2, 0))
    val matchInfo = MatchDay(
      id = "some-match-id",
      date = ZonedDateTime.parse("2000-01-01T00:00:00Z"),
      competition = Some(Competition(id = "1", name = "FA Cup")),
      stage = Stage("1"),
      round = Round("1", None),
      leg = "home",
      liveMatch = true,
      result =  false,
      previewAvailable = false,
      reportAvailable = false,
      lineupsAvailable = false,
      matchStatus = "KO",
      attendance = None,
      homeTeam = home,
      awayTeam = away,
      referee = None,
      venue = Some(Venue(id = "1", name = "Wembley")),
      comments = None
    )
  }

  trait WorldCupContext extends Scope {
    val builder = new MatchStatusNotificationBuilder("http://localhost")

    def loadFile(file: String): String = {
      val stream = this.getClass.getClassLoader.getResourceAsStream(file)
      Source.fromInputStream(stream).mkString
    }

    def matchInfo: MatchDay = Parser.parseMatchDay(loadFile("worldcup/match-day.xml")).head
    def rawEvents = Parser.parseMatchEvents(loadFile("worldcup/match-event-feed.xml")).get.events
    def allEvents: List[FootballMatchEvent] =
      new SyntheticMatchEventGenerator(() => ZonedDateTime.now())
        .generate(rawEvents, matchInfo.id, matchInfo)
        .flatMap(FootballMatchEvent.fromPaMatchEvent(matchInfo.homeTeam, matchInfo.awayTeam)(_))
    def dismissals = allEvents.collect { case d: Dismissal => d }
  }
}
