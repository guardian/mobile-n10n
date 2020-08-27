package football.models

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pa.{MatchDayTeam, Player}

class DismissalSpec  extends Specification with Mockito {
  "MatchPhaseEvent" should {
    "Create a Dismissal event" in  new DismissalScope {
      val event = mock[pa.MatchEvent]
      event.id returns Some("event-1")
      event.eventType returns "dismissal"
      event.eventTime returns Some("85")
      event.reason returns Some("Violent Conduct")
      event.players returns List(Player("player-1", "home-1", "player-1"), Player("", "", ""))
      event.addedTime returns Some("5:00")
      Dismissal.fromEvent(home,away)(event) should beSome(Dismissal("event-1", "player-1", home, 85, Some("5:00")))
    }
  }
  trait DismissalScope extends Scope {
    val home = MatchDayTeam(
      id = "home-1",
      name = "Home Side",
      score = None,
      htScore = None,
      aggregateScore = None,
      scorers = None
    )
    val away = MatchDayTeam(
      id = "away-1",
      name = "Away Side",
      score = None,
      htScore = None,
      aggregateScore = None,
      scorers = None
    )
  }
}
