package com.gu.mobile.notifications.football.lib

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pa.{Competition, MatchDay, MatchDayTeam, Round, Stage}

import java.time.ZonedDateTime

class FootballDataSpec extends Specification {

  trait FootballDataScope extends Scope {
    val home = MatchDayTeam("1", "Arsenal", None, None, None, None)
    val away = MatchDayTeam("2", "Chelsea", None, None, None, None)

    def matchDayWithCompetition(competitionId: String, matchId: String = "match-1"): MatchDay = MatchDay(
      id = matchId,
      date = ZonedDateTime.parse("2026-05-18T15:00:00Z"),
      competition = Some(Competition(competitionId, "Test Competition")),
      stage = Stage("1"),
      round = Round("1", None),
      leg = "1",
      liveMatch = true,
      result = false,
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

    val matchDayNoCompetition: MatchDay = MatchDay(
      id = "match-no-comp",
      date = ZonedDateTime.parse("2026-05-18T15:00:00Z"),
      competition = None,
      stage = Stage("1"),
      round = Round("1", None),
      leg = "1",
      liveMatch = true,
      result = false,
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

    val premierLeague = PACompetition("100", "football/premierleague", "Premier League 25/26", "PL")
    val championsLeague = PACompetition("500", "football/championsleague", "Champions League 25/26", "UCL")
    val supportedCompetitions = List(premierLeague, championsLeague)

  }

  "competitionIsSupported" should {

    val footballData = new FootballData(null, null, null, "test") // pass mocks or nulls as needed

    "return a match when its competition is in the supported list" in new FootballDataScope {
      val matches = List(matchDayWithCompetition("100"))
      footballData.competitionIsSupported(matches, supportedCompetitions) must haveSize(1)
    }

    "filter out a match when its competition is not in the supported list" in new FootballDataScope {
      val matches = List(matchDayWithCompetition("999"))
      footballData.competitionIsSupported(matches, supportedCompetitions) must beEmpty
    }

    "return only supported matches from a mixed list" in new FootballDataScope {
      val matches = List(
        matchDayWithCompetition("100", "match-1"),
        matchDayWithCompetition("500", "match-2"),
        matchDayWithCompetition("999", "match-3")
      )
      val result = footballData.competitionIsSupported(matches, supportedCompetitions)
      result must haveSize(2)
      result.map(_.id) must containAllOf(Seq("match-1", "match-2"))
    }

    "return all matches when supported competitions list is empty" in new FootballDataScope {
      val matches = List(
        matchDayWithCompetition("100", "match-1"),
        matchDayWithCompetition("999", "match-2")
      )
      footballData.competitionIsSupported(matches, List.empty) must haveSize(2)
    }

    "filter out a match with no competition" in new FootballDataScope {
      val matches = List(matchDayNoCompetition)
      footballData.competitionIsSupported(matches, supportedCompetitions) must beEmpty
    }

    "return an empty list when the input matches list is empty" in new FootballDataScope {
      footballData.competitionIsSupported(List.empty, supportedCompetitions) must beEmpty
    }
  }

  "paProvideAlerts" should {

    val footballData = new FootballData(null, null, null, "test")

    def matchWith(competitionId: String, homeId: String = "1", awayId: String = "2", roundNumber: String = "5"): MatchDay =
      MatchDay(
        id = "match-1",
        date = ZonedDateTime.parse("2026-05-18T15:00:00Z"),
        competition = Some(Competition(competitionId, "Test Competition")),
        stage = Stage("1"),
        round = Round(roundNumber, None),
        leg = "1",
        liveMatch = true,
        result = false,
        previewAvailable = false,
        reportAvailable = false,
        lineupsAvailable = false,
        matchStatus = "FH",
        attendance = None,
        homeTeam = MatchDayTeam(homeId, "Home", None, None, None, None),
        awayTeam = MatchDayTeam(awayId, "Away", None, None, None, None),
        referee = None,
        venue = None,
        comments = None
      )

    // Normal competitions
    "return true for a regular competition (e.g. Premier League)" in {
      footballData.paProvideAlerts(matchWith("100")) must beTrue
    }

    "return false when match has no competition" in {
      val m = matchWith("100").copy(competition = None)
      footballData.paProvideAlerts(m) must beFalse
    }

    // International friendlies (competition id 721)
    "return true for an international friendly where home team is covered (e.g. England 497)" in {
      footballData.paProvideAlerts(matchWith("721", homeId = "497", awayId = "37293")) must beTrue
    }

    "return true for an international friendly where away team is covered (e.g. Denmark 986)" in {
      footballData.paProvideAlerts(matchWith("721", homeId = "37293", awayId = "986")) must beTrue
    }

    "return true for an international friendly where both teams are covered" in {
      footballData.paProvideAlerts(matchWith("721", homeId = "629", awayId = "986")) must beTrue
    }

    "return false for an international friendly where neither team is covered (e.g. Congo DR v Algeria)" in {
      footballData.paProvideAlerts(matchWith("721", homeId = "37293", awayId = "37264")) must beFalse
    }

    // FA Cup qualifying (competition id 303)
    "return false for FA Cup round 1 (early qualifying)" in {
      footballData.paProvideAlerts(matchWith("303", roundNumber = "1")) must beFalse
    }

    "return false for FA Cup round 2 (early qualifying)" in {
      footballData.paProvideAlerts(matchWith("303", roundNumber = "2")) must beFalse
    }

    "return true for FA Cup round 3 (not early qualifying)" in {
      footballData.paProvideAlerts(matchWith("303", roundNumber = "3")) must beTrue
    }

    "return true for FA Cup round 4" in {
      footballData.paProvideAlerts(matchWith("303", roundNumber = "4")) must beTrue
    }

    "return true for FA Cup when round number is non-numeric" in {
      footballData.paProvideAlerts(matchWith("303", roundNumber = "Final")) must beTrue
    }
  }
}
