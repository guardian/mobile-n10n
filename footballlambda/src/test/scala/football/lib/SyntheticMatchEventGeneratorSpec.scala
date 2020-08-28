package football.lib

import java.time.ZonedDateTime
import java.util.UUID

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pa.{MatchDay, MatchDayTeam, Round, Stage}

class SyntheticMatchEventGeneratorSpec extends Specification {

  trait TestScope extends Scope {
    val home = MatchDayTeam("1", "Liverpool", None, None, None, None)
    val away = MatchDayTeam("2", "Plymouth", None, None, None, None)

    val matchInfo = MatchDay(
      id = "some-match-id",
      date = ZonedDateTime.parse("2000-01-01T00:00:00Z"),
      competition = None,
      stage = Stage("1"),
      round = Round("1", None),
      leg = "home",
      liveMatch = true,
      result =  false,
      previewAvailable = false,
      reportAvailable = false,
      lineupsAvailable = false,
      matchStatus = "FH",
      attendance = None,
      homeTeam = home,
      awayTeam = away,
      referee = None,
      venue = None,
      comments = None
    )

    val timelineEvent = pa.MatchEvent(
      id = None,
      teamID = None,
      eventType = "timeline",
      matchTime = Some("0:00"),
      eventTime = None,
      addedTime = None,
      players = List.empty,
      reason = None,
      how = None,
      whereFrom = None,
      whereTo = None,
      distance = None,
      outcome = None
    )

    val kickoffId = UUID.nameUUIDFromBytes(s"football-match/match-id/timeline/00:00".getBytes).toString
  }

  "A SyntheticMatchEvent generator" should {
    "Add id to first timeline event" in new TestScope {
      val generator = new SyntheticMatchEventGenerator()
      generator.generate(List(timelineEvent), "match-id", matchInfo) mustEqual List(timelineEvent.copy(id = Some(kickoffId)))
    }
    "Add half-time event if status is HT" in new TestScope {
      val generator = new SyntheticMatchEventGenerator()
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(matchStatus = "HT")).drop(1).head.eventType mustEqual "half-time"
    }
    "Add second-half event if status is SHS" in new TestScope {
      val generator = new SyntheticMatchEventGenerator()
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(matchStatus = "SHS")).drop(1).head.eventType mustEqual "second-half"
    }
    "Add full-time event if match is result" in new TestScope {
      val generator = new SyntheticMatchEventGenerator()
      generator.generate(List(timelineEvent), "match-id", matchInfo.copy(result = true)).drop(1).head.eventType mustEqual "full-time"
    }
  }
}
