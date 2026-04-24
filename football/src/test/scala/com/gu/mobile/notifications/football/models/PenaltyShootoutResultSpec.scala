package com.gu.mobile.notifications.football.models

import com.gu.mobile.notifications.client.models.{MissedShootoutResult, SavedShootoutResult, ScoredShootoutResult}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pa.{MatchDayTeam, MatchEvent, Player}

class PenaltyShootoutResultSpec extends Specification {

  trait ShootoutScope extends Scope {
    val home = MatchDayTeam("home-1", "Arsenal", score = Some(1), htScore = Some(1), aggregateScore = None, scorers = None)
    val away = MatchDayTeam("away-1", "Chelsea", score = Some(1), htScore = Some(1), aggregateScore = None, scorers = None)

    val homePlayer = Player(id = "p1", teamID = "home-1", name = "Saka")
    val awayPlayer = Player(id = "p2", teamID = "away-1", name = "Palmer")

    def shootoutEvent(eventType: String, player: Player, id: String = "evt-1") = MatchEvent(
      id = Some(id),
      teamID = Some(player.teamID),
      eventType = eventType,
      matchTime = Some("90"),
      eventTime = Some("90"),
      addedTime = None,
      players = List(player),
      reason = None,
      how = None,
      whereFrom = None,
      whereTo = None,
      distance = None,
      outcome = None
    )
  }

  "PenaltyShootoutResult.fromEvent" should {

    "parse a shootoutGoal as ScoredShootoutResult for the correct team" in new ShootoutScope {
      val event = shootoutEvent("shootoutGoal", homePlayer)
      val result = PenaltyShootoutResult.fromEvent(home, away)(event)
      result must beSome
      result.get.result mustEqual ScoredShootoutResult
      result.get.kickingTeam mustEqual home
      result.get.otherTeam mustEqual away
      result.get.playerName mustEqual "Saka"
    }

    "parse a shootoutMiss as MissedShootoutResult for the correct team" in new ShootoutScope {
      val event = shootoutEvent("shootoutMiss", awayPlayer, "evt-2")
      val result = PenaltyShootoutResult.fromEvent(home, away)(event)
      result must beSome
      result.get.result mustEqual MissedShootoutResult
      result.get.kickingTeam mustEqual away
      result.get.otherTeam mustEqual home
      result.get.playerName mustEqual "Palmer"
    }

    "parse a shootoutSave as SavedShootoutResult for the correct team" in new ShootoutScope {
      val event = shootoutEvent("shootoutSave", homePlayer, "evt-3")
      val result = PenaltyShootoutResult.fromEvent(home, away)(event)
      result must beSome
      result.get.kickingTeam mustEqual home
      result.get.otherTeam mustEqual away
      result.get.result mustEqual SavedShootoutResult
    }

    "return None for an unrecognised event type" in new ShootoutScope {
      val event = shootoutEvent("goal", homePlayer)
      PenaltyShootoutResult.fromEvent(home, away)(event) must beNone
    }

    "return None when the event has no id" in new ShootoutScope {
      val event = shootoutEvent("shootoutGoal", homePlayer).copy(id = None)
      PenaltyShootoutResult.fromEvent(home, away)(event) must beNone
    }

    "return None when there are no players on the event" in new ShootoutScope {
      val event = shootoutEvent("shootoutGoal", homePlayer).copy(players = List.empty)
      PenaltyShootoutResult.fromEvent(home, away)(event) must beNone
    }

    "return None when eventTime is missing" in new ShootoutScope {
      val event = shootoutEvent("shootoutGoal", homePlayer).copy(eventTime = None)
      PenaltyShootoutResult.fromEvent(home, away)(event) must beNone
    }

    "return None when eventTime is not an integer" in new ShootoutScope {
      val event = shootoutEvent("shootoutGoal", homePlayer).copy(eventTime = Some("90+3"))
      PenaltyShootoutResult.fromEvent(home, away)(event) must beNone
    }
  }
}

