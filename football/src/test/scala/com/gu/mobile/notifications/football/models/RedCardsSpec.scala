package com.gu.mobile.notifications.football.models

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pa.{MatchDayTeam, Player}

class RedCardsSpec extends Specification {

  "RedCards.fromDismissals" should {

    "return zero red cards for both teams when there are no dismissals" in new RedCardsScope {
      val result = RedCards.fromDismissals(home, away, List.empty)
      result mustEqual RedCards(0, 0)
    }

    "count a red card for the home team" in new RedCardsScope {
      val dismissals = List(homeDismissal("event-1", "player-1"))
      val result = RedCards.fromDismissals(home, away, dismissals)
      result mustEqual RedCards(home = 1, away = 0)
    }

    "count a red card for the away team" in new RedCardsScope {
      val dismissals = List(awayDismissal("event-2", "player-2"))
      val result = RedCards.fromDismissals(home, away, dismissals)
      result mustEqual RedCards(home = 0, away = 1)
    }

    "count red cards for both teams independently" in new RedCardsScope {
      val dismissals = List(
        homeDismissal("event-1", "player-1"),
        awayDismissal("event-2", "player-2"),
        awayDismissal("event-3", "player-3")
      )
      val result = RedCards.fromDismissals(home, away, dismissals)
      result mustEqual RedCards(home = 1, away = 2)
    }
  }

  trait RedCardsScope extends Scope {
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

    def homeDismissal(eventId: String, playerName: String) =
      Dismissal(eventId, playerName, home, 80, None)

    def awayDismissal(eventId: String, playerName: String) =
      Dismissal(eventId, playerName, away, 75, None)
  }
}

