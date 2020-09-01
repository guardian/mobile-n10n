package com.gu.mobile.notifications.football.models

import com.gu.mobile.notifications.client.models.{DefaultGoalType, OwnGoalType, PenaltyGoalType}
import org.specs2.mutable.Specification
import pa.MatchDayTeam
import org.specs2.specification.Scope

class GoalSpec extends Specification {
  "A Goal" should {
    "Create a normal goal (home)" in new GoalScope {
      Goal.fromEvent(home, away)(homeGoal) should beSome(Goal(DefaultGoalType, homePlayer.name, home, away, 5, None, "1234"))
    }
    "Create a normal goal (away)" in new GoalScope {
      Goal.fromEvent(home, away)(awayGoal) should beSome(Goal(DefaultGoalType, awayPlayer.name, away, home, 5, None, "1234"))
    }
    "Create a normal goal in extra time" in new GoalScope {
      Goal.fromEvent(home, away)(homeGoal.copy(addedTime = Some("5:00"))) should beSome(Goal(DefaultGoalType, homePlayer.name, home, away, 5, Some("5:00"), "1234"))
    }
    "Create an own goal" in new GoalScope {
      Goal.fromEvent(home, away)(homeOwnGoal) should beSome(Goal(OwnGoalType, homePlayer.name, away, home, 5, None, "1234"))
    }
    "Create a penalty" in new GoalScope {
      Goal.fromEvent(home, away)(homePenalty) should beSome(Goal(PenaltyGoalType, homePlayer.name, home, away, 5, None, "1234"))
    }
  }

  trait GoalScope extends Scope {
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
    val homePlayer = pa.Player(id = "123", teamID = home.id, "A player")
    val awayPlayer = pa.Player(id = "456", teamID = away.id, "B player")
    val baseGoal = pa.MatchEvent(
      id = Some("1234"),
      teamID = None,
      eventType = "goal",
      matchTime = None,
      eventTime = Some("5"),
      addedTime = None,
      players = List(homePlayer),
      reason = None,
      how = None,
      whereFrom = None,
      whereTo = None,
      distance = None,
      outcome = None
    )

    val homeGoal = baseGoal
    val awayGoal = baseGoal.copy(players = List(awayPlayer))
    val homeOwnGoal = baseGoal.copy(eventType = "own goal")
    val homePenalty = baseGoal.copy(eventType = "goal from penalty")
  }
}
