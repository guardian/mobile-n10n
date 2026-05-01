package com.gu.mobile.notifications.football.models

import com.gu.mobile.notifications.client.models.{MissedShootoutResult, SavedShootoutResult, ScoredShootoutResult}
import com.gu.mobile.notifications.client.models.liveActitivites.PenaltyShootoutState
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pa.MatchDayTeam

class PenaltyShootoutScoreSpec extends Specification {

  trait ShootoutScope extends Scope {
    val home = MatchDayTeam("home-1", "Arsenal", score = Some(1), htScore = Some(1), aggregateScore = None, scorers = None)
    val away = MatchDayTeam("away-1", "Chelsea", score = Some(1), htScore = Some(1), aggregateScore = None, scorers = None)
  }

  "PenaltyShootoutScore.fromPenaltyShootoutResult" should {

    "return None when the list is empty" in new ShootoutScope {
      PenaltyShootoutScore.fromPenaltyShootoutKicks(home, away, List.empty) must beNone
    }

    "count scored, missed and saved correctly for each team" in new ShootoutScope {
      val results = List(
        PenaltyShootoutKick(ScoredShootoutResult, "Saka",    home, away, 90, "e1"),
        PenaltyShootoutKick(ScoredShootoutResult, "Saka",    home, away, 91, "e2"),
        PenaltyShootoutKick(MissedShootoutResult, "Havertz", home, away, 92, "e3"),
        PenaltyShootoutKick(ScoredShootoutResult, "Palmer",  away, home, 90, "e4"),
        PenaltyShootoutKick(SavedShootoutResult,  "Jackson", away, home, 91, "e5"),
        PenaltyShootoutKick(MissedShootoutResult, "Nkunku",  away, home, 92, "e6")
      )

      val score = PenaltyShootoutScore.fromPenaltyShootoutKicks(home, away, results)
      score must beSome(PenaltyShootoutScore(
        homeScored = 2, homeMissed = 1, homeSaved = 0,
        awayScored = 1, awayMissed = 1, awaySaved = 1
      ))
    }

    "handle all home team shootout events and all away team shootout events" in new ShootoutScope {
      val results = List(
        PenaltyShootoutKick(ScoredShootoutResult, "Saka",   home, away, 90, "e1"),
        PenaltyShootoutKick(SavedShootoutResult,  "Palmer", away, home, 90, "e2")
      )
      val score = PenaltyShootoutScore.fromPenaltyShootoutKicks(home, away, results)
      score must beSome(PenaltyShootoutScore(
        homeScored = 1, homeMissed = 0, homeSaved = 0,
        awayScored = 0, awayMissed = 0, awaySaved = 1
      ))
    }
  }

  "PenaltyShootoutScore.toPenaltyShootoutState" should {

    "return None when score is None" in {
      PenaltyShootoutScore.toPenaltyShootoutState(None, isHomeTeam = true) must beNone
      PenaltyShootoutScore.toPenaltyShootoutState(None, isHomeTeam = false) must beNone
    }

    "return home team perspective when isHomeTeam is true" in {
      val score = Some(PenaltyShootoutScore(homeScored = 3, homeMissed = 1, homeSaved = 0, awayScored = 2, awayMissed = 0, awaySaved = 1))
      PenaltyShootoutScore.toPenaltyShootoutState(score, isHomeTeam = true) must
        beSome(PenaltyShootoutState(scored = 3, missed = 1, saved = 0))
    }

    "return away team perspective when isHomeTeam is false" in {
      val score = Some(PenaltyShootoutScore(homeScored = 3, homeMissed = 1, homeSaved = 0, awayScored = 2, awayMissed = 0, awaySaved = 1))
      PenaltyShootoutScore.toPenaltyShootoutState(score, isHomeTeam = false) must
        beSome(PenaltyShootoutState(scored = 2, missed = 0, saved = 1))
    }
  }
}
