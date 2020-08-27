package football.models

import com.gu.mobile.notifications.client.models.{DefaultGoalType, OwnGoalType, PenaltyGoalType}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pa.MatchDayTeam

class ScoreSpec extends Specification {

  "Score" should {
    "calculate correct score for normal goals" in new GoalScope {
      Score.fromGoals(home, away, List(homeGoal, awayGoal, homeGoal, homeGoal)) mustEqual Score(3, 1)
    }

    "calculate correct score with home own-goal" in new GoalScope {
      Score.fromGoals(home, away, List(homeOwnGoal, awayGoal, homeGoal)) mustEqual Score(2, 1)
    }

    "calculate correct score with away own-goal" in new GoalScope {
      Score.fromGoals(home, away, List(awayOwnGoal, awayGoal, homeGoal)) mustEqual Score(1, 2)
    }

    "calculate correct score with home penalty" in new GoalScope {
      Score.fromGoals(home, away, List(homeGoal, awayGoal, homeGoal, homePenalty)) mustEqual Score(3, 1)
    }

    "calculate correct score with away penalty" in new GoalScope {
      Score.fromGoals(home, away, List(homeGoal, awayGoal, homeGoal, awayPenalty)) mustEqual Score(2, 2)
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

    val homeGoal = Goal(
      goalType = DefaultGoalType,
      scorerName =  "A",
      scoringTeam = home,
      otherTeam = away,
      minute = 5,
      addedTime = None,
      ""
    )
    val awayGoal = Goal(
      goalType = DefaultGoalType,
      scorerName =  "B",
      scoringTeam = away,
      otherTeam = home,
      minute = 5,
      addedTime = None,
      ""
    )
    val homePenalty = Goal(
      goalType = PenaltyGoalType,
      scorerName =  "C",
      scoringTeam = home,
      otherTeam = away,
      minute = 5,
      addedTime = None,
      ""
    )
    val awayPenalty = Goal(
      goalType = PenaltyGoalType,
      scorerName =  "D",
      scoringTeam = away,
      otherTeam = home,
      minute = 5,
      addedTime = None,
      ""
    )
    val homeOwnGoal = Goal(
      goalType = OwnGoalType,
      scorerName =  "E",
      scoringTeam = home,
      otherTeam = away,
      minute = 5,
      addedTime = None,
      ""
    )
    val awayOwnGoal = Goal(
      goalType = OwnGoalType,
      scorerName =  "F",
      scoringTeam = away,
      otherTeam = home,
      minute = 5,
      addedTime = None,
      ""
    )
  }
}
