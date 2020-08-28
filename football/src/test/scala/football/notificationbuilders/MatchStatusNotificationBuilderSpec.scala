package football.notificationbuilders

import java.net.URI
import java.time.ZonedDateTime
import java.util.UUID

import com.gu.mobile.notifications.client.models.Importance.Major
import com.gu.mobile.notifications.client.models._
import football.models.{Goal, GoalContext, Score}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pa.{Competition, MatchDay, MatchDayTeam, Round, Stage, Venue}


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
        homeTeamName = "Liverpool",
        homeTeamId = "1",
        homeTeamScore = 1,
        homeTeamMessage = "Steve 5'",
        matchId = "some-match-id",
        matchInfoUri = new URI("http://localhost/sport/football/matches/some-match-id"),
        articleUri = Some(new URI("http://localhost/items/football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live")),
        importance = Major,
        topic = List(Topic(TopicTypes.FootballTeam, "1"), Topic(TopicTypes.FootballTeam, "2"), Topic(TopicTypes.FootballMatch, "some-match-id")),
        eventId = UUID.nameUUIDFromBytes("".getBytes).toString,
        matchStatus = "1st",
        debug = false,
        competitionName = Some("FA Cup"),
        venue = Some("Wembley"),
        dryRun = None
      )
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
      matchStatus = "1st",
      attendance = None,
      homeTeam = home,
      awayTeam = away,
      referee = None,
      venue = Some(Venue(id = "1", name = "Wembley")),
      comments = None
    )
  }
}
