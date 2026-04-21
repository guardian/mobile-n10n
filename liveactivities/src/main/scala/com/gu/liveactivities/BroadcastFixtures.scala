package com.gu.liveactivities

import com.gu.liveactivities.models._
import com.gu.mobile.notifications.client.models.liveActitivites.{Competition, FootballMatchContentState, FullTime, Scheduled, SecondHalf, TeamState}

// TODO these are for dev purposes only and should be deleted.
object BroadcastFixtures {
  def fifteenMinutesFromNowEpochSeconds: Long = (System.currentTimeMillis() / 1000L) + 15 * 60
  def nowEpochSeconds: Long = System.currentTimeMillis() / 1000L


  val broadcastStartBodyFixture = BroadcastStartBody(
    aps = BroadcastStartAps(
      timestamp = fifteenMinutesFromNowEpochSeconds,
      event = "start",
      `content-state` = FootballMatchContentState(
        matchStatus = Scheduled, // "New Match" will be mapped to Scheduled as an example
        kickOffTimestamp = fifteenMinutesFromNowEpochSeconds,
        homeTeam = TeamState(
          name = "Brentford",
          score = 1,
          logoAssetName = None,
          teamUrl = None,
          penaltyScore = None,
          redCards = None
        ),
        awayTeam = TeamState(
          name = "Wolverhampton",
          score = 1,
          logoAssetName = None,
          teamUrl = None,
          penaltyScore = None,
          redCards = None
        ),
        competition = Competition(
          name = "FIFA World Cup 2026",
          round = Some("Group A")
        ),
        commentary = None,
        lineupsAvailable = Some(false),
        currentMinute = None,
        currentPeriodStartTime = None,
        articleUrl = None
      ),
      `attributes-type` = FootballMatchAttributesType,
      `attributes` = FootballMatchAttributes(
        matchId = "4540277"
      )
    )
  )


  val broadcastUpdateBodyFixture = BroadcastUpdateBody(
    aps = BroadcastUpdateAps(
      timestamp = nowEpochSeconds,
      event = "update",
      `content-state` = FootballMatchContentState(
        matchStatus = SecondHalf,
        kickOffTimestamp = 1742131800L, // 2025-03-16T13:30:00 as epoch seconds
        homeTeam = TeamState(
          name = "Arsenal",
          score = 2,
          logoAssetName = None,
          teamUrl = None,
          penaltyScore = None,
          redCards = Some(0)
        ),
        awayTeam = TeamState(
          name = "Chelsea",
          score = 2,
          logoAssetName = None,
          teamUrl = None,
          penaltyScore = None,
          redCards = Some(0)
        ),
        competition = Competition(
          name = "FIFA World Cup 2026",
          round = Some("Group A")
        ),
        commentary = Some("A closely fought match so far..."),
        lineupsAvailable = Some(true),
        currentMinute = Some(75),
        currentPeriodStartTime = None,
        articleUrl = None
      ),
      `stale-date` = nowEpochSeconds + 3600 // 1 hour from now
    )
  )

  val broadcastEndBodyFixture = BroadcastEndBody(
    aps = BroadcastEndAps(
      timestamp = nowEpochSeconds,
      event = "end",
      `content-state` = FootballMatchContentState(
        matchStatus = FullTime,
        kickOffTimestamp = 1742131800L, // 2025-03-16T13:30:00 as epoch seconds
        homeTeam = TeamState(
          name = "Arsenal",
          score = 3,
          logoAssetName = None,
          teamUrl = None,
          penaltyScore = None,
          redCards = Some(0)
        ),
        awayTeam = TeamState(
          name = "Chelsea",
          score = 3,
          logoAssetName = None,
          teamUrl = None,
          penaltyScore = None,
          redCards = Some(1)
        ),
        competition = Competition(
          name = "FIFA World Cup 2026",
          round = Some("Group A")
        ),
        commentary = Some("Arsenal takes the win thanks to a late goal."),
        lineupsAvailable = Some(true),
        currentMinute = Some(90),
        currentPeriodStartTime = None,
        articleUrl = None
      ),
      `dismissal-date` = nowEpochSeconds + 60 // 1 min from now
    )
  )
}