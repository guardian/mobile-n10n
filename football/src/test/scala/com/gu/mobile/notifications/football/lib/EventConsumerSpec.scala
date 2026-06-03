package com.gu.mobile.notifications.football.lib

import java.net.URI
import com.gu.mobile.notifications.client.models.{liveActitivites, _}
import com.gu.mobile.notifications.client.models.Importance.{Major, Minor}
import com.gu.mobile.notifications.client.models.TopicTypes.{
  FootballMatch,
  FootballTeam,
  FootballTeamLiveActivity,
  FootballMatchLiveActivity
}
import com.gu.mobile.notifications.football.models.{
  MatchDataWithArticle,
  PenaltyShootoutKick
}
import com.gu.mobile.notifications.client.models.liveActitivites._
import com.gu.mobile.notifications.football.notificationbuilders.{
  MatchStatusLiveActivityPayloadBuilder,
  MatchStatusNotificationBuilder
}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pa.{MatchDay, MatchEvent, Parser}

import java.time.ZonedDateTime
import scala.io.Source

class EventConsumerSpec(implicit ev: ExecutionEnv)
    extends Specification
    with Mockito {
  "An Notifications EventConsumer" should {
    "generate a kick-off notification" in new MatchEventsContext {
      override def matchDay: MatchDay = super.matchDay.copy(matchStatus = "KO")

      val result: List[NotificationPayload] =
        eventConsumer.eventsToNotifications(matchData)

      val expectedNotification = FootballMatchStatusPayload(
        title = Some("Kick-off!"),
        message = Some("Arsenal 0-0 Leicester (1st)"),
        thumbnailUrl = None,
        sender = "mobile-notifications-football-lambda",
        awayTeamName = "Leicester",
        awayTeamScore = 0,
        awayTeamMessage = " ",
        awayTeamId = "29",
        awayTeamRedCards = 0,
        homeTeamName = "Arsenal",
        homeTeamScore = 0,
        homeTeamMessage = " ",
        homeTeamId = "1006",
        homeTeamRedCards = 0,
        competitionName = Some("Premier League 17/18"),
        roundName = Some("League"),
        venue = Some("Emirates Stadium"),
        matchId = "4011135",
        matchInfoUri = new URI(
          "https://mobile.guardianapis.com/sport/football/matches/4011135"
        ),
        articleUri = Some(
          new URI(
            "https://mobile.guardianapis.com/items/football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live"
          )
        ),
        importance = Minor,
        topic = List(
          Topic(FootballTeam, "1006"),
          Topic(FootballTeam, "29"),
          Topic(FootballMatch, "4011135")
        ),
        matchStatus = "1st",
        eventId = "7e730fbe-b013-3a0e-89cb-12b46260d7be",
        kickOffTimestamp = Some(1502477100L),
        lineupsAvailable = Some(true),
        detailedMatchStatus = Some("FIRST_HALF"),
        debug = false,
        dryRun = None
      )

      result should contain(expectedNotification)
    }

    "generate half-time notification" in new MatchEventsContext {
      override def matchDay: MatchDay = super.matchDay.copy(matchStatus = "HT")

      val result: List[NotificationPayload] =
        eventConsumer.eventsToNotifications(matchData)

      val expectedNotification = FootballMatchStatusPayload(
        title = Some("Half-time"),
        message = Some("Arsenal 3-0 Leicester (HT)"),
        thumbnailUrl = None,
        sender = "mobile-notifications-football-lambda",
        awayTeamName = "Leicester",
        awayTeamScore = 0,
        awayTeamMessage = " ",
        awayTeamId = "29",
        awayTeamRedCards = 0,
        homeTeamName = "Arsenal",
        homeTeamScore = 3,
        homeTeamMessage =
          "Henrikh Mkhitaryan 10'\nSofiane Hanni 32'\nRed card: Carl Jenkinson 106'\nMarcus Rashford 107'\nRed card: Henrikh Mkhitaryan 114'",
        homeTeamId = "1006",
        homeTeamRedCards = 2,
        competitionName = Some("Premier League 17/18"),
        roundName = Some("League"),
        venue = Some("Emirates Stadium"),
        matchId = "4011135",
        matchInfoUri = new URI(
          "https://mobile.guardianapis.com/sport/football/matches/4011135"
        ),
        articleUri = Some(
          new URI(
            "https://mobile.guardianapis.com/items/football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live"
          )
        ),
        importance = Minor,
        topic = List(
          Topic(FootballTeam, "1006"),
          Topic(FootballTeam, "29"),
          Topic(FootballMatch, "4011135")
        ),
        matchStatus = "HT",
        eventId = "bb346058-64d0-3ab1-9016-ea19d90837f0",
        kickOffTimestamp = Some(1502477100L),
        lineupsAvailable = Some(true),
        detailedMatchStatus = Some("HALF_TIME"),
        debug = false,
        dryRun = None
      )

      result should contain(expectedNotification)
    }

    "generate second half start notification" in new MatchEventsContext {
      // 23572566 is the first event of the second half
      override def rawEvents: List[MatchEvent] =
        super.rawEvents.takeWhile(!_.id.contains("23572566"))
      override def matchDay: MatchDay = super.matchDay.copy(matchStatus = "SHS")

      val result: List[NotificationPayload] =
        eventConsumer.eventsToNotifications(matchData)

      val expectedNotification = FootballMatchStatusPayload(
        title = Some("Second-half start"),
        message = Some("Arsenal 2-0 Leicester (2nd)"),
        thumbnailUrl = None,
        sender = "mobile-notifications-football-lambda",
        awayTeamName = "Leicester",
        awayTeamScore = 0,
        awayTeamMessage = " ",
        awayTeamId = "29",
        awayTeamRedCards = 0,
        homeTeamName = "Arsenal",
        homeTeamScore = 2,
        homeTeamMessage = "Henrikh Mkhitaryan 10'\nSofiane Hanni 32'",
        homeTeamId = "1006",
        homeTeamRedCards = 0,
        competitionName = Some("Premier League 17/18"),
        roundName = Some("League"),
        venue = Some("Emirates Stadium"),
        matchId = "4011135",
        matchInfoUri = new URI(
          "https://mobile.guardianapis.com/sport/football/matches/4011135"
        ),
        articleUri = Some(
          new URI(
            "https://mobile.guardianapis.com/items/football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live"
          )
        ),
        importance = Minor,
        topic = List(
          Topic(FootballTeam, "1006"),
          Topic(FootballTeam, "29"),
          Topic(FootballMatch, "4011135")
        ),
        matchStatus = "2nd",
        eventId = "a45dfca1-ead9-3d8c-bf83-c4966a737b05",
        kickOffTimestamp = Some(1502477100L),
        lineupsAvailable = Some(true),
        detailedMatchStatus = Some("SECOND_HALF"),
        debug = false,
        dryRun = None
      )

      result should contain(expectedNotification)
    }

    "generate full time notification" in new MatchEventsContext {
      override def matchDay: MatchDay =
        super.matchDay.copy(matchStatus = "FT", result = true)

      val result: List[NotificationPayload] =
        eventConsumer.eventsToNotifications(matchData)

      val expectedNotification = FootballMatchStatusPayload(
        title = Some("Full-Time"),
        message = Some("Arsenal 3-0 Leicester (FT)"),
        thumbnailUrl = None,
        sender = "mobile-notifications-football-lambda",
        awayTeamName = "Leicester",
        awayTeamScore = 0,
        awayTeamMessage = " ",
        awayTeamId = "29",
        awayTeamRedCards = 0,
        homeTeamName = "Arsenal",
        homeTeamScore = 3,
        homeTeamMessage =
          "Henrikh Mkhitaryan 10'\nSofiane Hanni 32'\nRed card: Carl Jenkinson 106'\nMarcus Rashford 107'\nRed card: Henrikh Mkhitaryan 114'",
        homeTeamId = "1006",
        homeTeamRedCards = 2,
        competitionName = Some("Premier League 17/18"),
        roundName = Some("League"),
        venue = Some("Emirates Stadium"),
        matchId = "4011135",
        matchInfoUri = new URI(
          "https://mobile.guardianapis.com/sport/football/matches/4011135"
        ),
        articleUri = Some(
          new URI(
            "https://mobile.guardianapis.com/items/football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live"
          )
        ),
        importance = Minor,
        topic = List(
          Topic(FootballTeam, "1006"),
          Topic(FootballTeam, "29"),
          Topic(FootballMatch, "4011135")
        ),
        matchStatus = "FT",
        eventId = "d59c9939-8199-3b8b-ad63-16aa020c1a73",
        kickOffTimestamp = Some(1502477100L),
        lineupsAvailable = Some(true),
        detailedMatchStatus = Some("FULL_TIME"),
        debug = false,
        dryRun = None
      )

      result should contain(expectedNotification)
    }

    "generate goal notifications from FootballMatchStatusPayload" in new MatchEventsContext {
      override def matchDay: MatchDay =
        super.matchDay.copy(matchStatus = "KO", result = true)

      val result: List[NotificationPayload] =
        eventConsumer.eventsToNotifications(matchData)

      val expectedNotification = FootballMatchStatusPayload(
        title = Some("Goal!"),
        message = Some("Arsenal 1-0 Leicester (1st)\nHenrikh Mkhitaryan 10min"),
        thumbnailUrl = None,
        sender = "mobile-notifications-football-lambda",
        awayTeamName = "Leicester",
        awayTeamScore = 0,
        awayTeamMessage = " ",
        awayTeamId = "29",
        awayTeamRedCards = 0,
        homeTeamName = "Arsenal",
        homeTeamScore = 1,
        homeTeamMessage = "Henrikh Mkhitaryan 10'",
        homeTeamId = "1006",
        homeTeamRedCards = 0,
        competitionName = Some("Premier League 17/18"),
        roundName = Some("League"),
        venue = Some("Emirates Stadium"),
        matchId = "4011135",
        matchInfoUri = new URI(
          "https://mobile.guardianapis.com/sport/football/matches/4011135"
        ),
        articleUri = Some(
          new URI(
            "https://mobile.guardianapis.com/items/football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live"
          )
        ),
        importance = Major,
        topic = List(
          Topic(FootballTeam, "1006"),
          Topic(FootballTeam, "29"),
          Topic(FootballMatch, "4011135")
        ),
        matchStatus = "1st",
        eventId = "1c8d67f9-0f32-342a-8543-aa3e21ee7da4",
        kickOffTimestamp = Some(1502477100L),
        lineupsAvailable = Some(true),
        detailedMatchStatus = Some("FIRST_HALF"),
        debug = false,
        dryRun = None
      )

      result should contain(expectedNotification)
    }
    "generate red card notifications from FootballMatchStatusPayload" in new MatchEventsContext {
      override def matchDay: MatchDay =
        super.matchDay.copy(matchStatus = "KO", result = true)
      val result: List[NotificationPayload] =
        eventConsumer.eventsToNotifications(matchData)

      val expectedNotification = FootballMatchStatusPayload(
        title = Some("Red card"),
        message = Some("Arsenal 3-0 Leicester (1st)\nHenrikh Mkhitaryan (Arsenal) 114min"),
        thumbnailUrl = None,
        sender = "mobile-notifications-football-lambda",
        awayTeamName = "Leicester",
        awayTeamScore = 0,
        awayTeamMessage = " ",
        awayTeamId = "29",
        awayTeamRedCards = 0,
        homeTeamName = "Arsenal",
        homeTeamScore = 3,
        homeTeamMessage = "Red card: Henrikh Mkhitaryan 114'\nHenrikh Mkhitaryan 10'\nSofiane Hanni 32'\nRed card: Carl Jenkinson 106'\nMarcus Rashford 107'",
        homeTeamId = "1006",
        homeTeamRedCards = 2,
        competitionName = Some("Premier League 17/18"),
        roundName = Some("League"),
        venue = Some("Emirates Stadium"),
        matchId = "4011135",
        matchInfoUri = new URI("https://mobile.guardianapis.com/sport/football/matches/4011135"),
        articleUri = Some(new URI("https://mobile.guardianapis.com/items/football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live")),
        importance = Minor,
        topic = List(
          Topic(FootballTeam, "1006"),
          Topic(FootballTeam, "29"),
          Topic(FootballMatch, "4011135")
        ),
        matchStatus = "1st",
        eventId = "7c92d6ca-9f20-398f-9510-eb4c179fb5ae",
        kickOffTimestamp = Some(1502477100L),
        lineupsAvailable = Some(true),
        detailedMatchStatus = Some("FIRST_HALF"),
        debug = false,
        dryRun = None
      )

      result should contain(expectedNotification)
    }
  }

  "A Notification EventConsumer" should {
    "not generate normal notifications for penalty shootout kicks" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDayLA.copy(date = ZonedDateTime.now().plusHours(1))

      val result: List[NotificationPayload] =
        eventConsumer.eventsToNotifications(matchDataLA)

      result must not contain((payload: NotificationPayload) =>
        payload.isInstanceOf[FootballMatchStatusPayload] && payload.title.contains("Penalty Kick")
      )
    }

    "generate FootballPenaltyShootoutPayload for shootout events" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDayLA.copy(date = ZonedDateTime.now().plusHours(1))

      val result: List[NotificationPayload] =
        eventConsumer.eventsToNotifications(matchDataLA)

      result must contain(beAnInstanceOf[FootballPenaltyShootoutPayload])
    }

    "NOT generate notification payload for extra time match phase events" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDayLA.copy(date = ZonedDateTime.now(), matchStatus = "ETS")

      val result: List[NotificationPayload] =
        eventConsumer.eventsToNotifications(matchDataLA)

      result must forall((payload: NotificationPayload) =>
        payload.title.getOrElse("") != "The Guardian"
      ) // default string for unknown events
    }

  }

  "A LiveActivity EventConsumer handles synthetic events and" should {

    "generate a CREATE CHANNEL payload" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDayLA.copy(date = ZonedDateTime.now().plusHours(1), liveMatch = false)
      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain((payload: LiveActivityPayload) =>
        payload.eventType == CreateChannelEvent
      )
      // note: this will trigger the downstream update from the channel manager with Scheduled Match Status.
    }

    "generate a END live activity payload when result is true and liveMatch is false" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "FT", result = true, liveMatch = false)
      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain((payload: LiveActivityPayload) =>
        payload.eventType == EndLiveActivityEvent
      )
    }

    "NOT generate a END live activity payload when result is true and liveMatch is true" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "FT", result = true, liveMatch = true)
      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain((payload: LiveActivityPayload) =>
        payload.eventType == UpdateLiveActivityEvent
      )
    }
  }

  "A LiveActivity EventConsumer handles synthetic events for match phases and" should {

    "generate a PreMatch live activity payload" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "-", result = false)

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .matchStatus == liveActitivites.PreMatch
      )
    }

    "generate a kick off first half start live activity payload" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "KO", result = false)

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .matchStatus == liveActitivites.FirstHalf
      )
    }

    "generate a half time live activity payload" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "HT", result = false)

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .matchStatus == liveActitivites.HalfTime
      )
    }

    "generate a second half live activity payload" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "SHS", result = false)

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain { (payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .matchStatus == liveActitivites.SecondHalf
      }
    }

    "generate a extra time to be played live activity payload" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "FTET", result = false)

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .matchStatus == liveActitivites.ExtraTimeToBePlayed
      )
    }

    "generate a extra time first half live activity payload" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "ETS", result = false)

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .matchStatus == liveActitivites.ExtraTimeFirstHalf
      )
    }

    "generate a extra time half time live activity payload" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "ETHT", result = false)

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .matchStatus == liveActitivites.ExtraTimeHalfTime
      )
    }

    "generate a extra time second half live activity payload" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "ETSHS", result = false)

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .matchStatus == liveActitivites.ExtraTimeSecondHalf
      )
    }

    "generate a penalty time to be played live activity payload" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "FTPT", result = false)

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .matchStatus == liveActitivites.PenaltiesToBePlayed
      )
    }

    "generate a penalty time live activity payload" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "PT", result = false)

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .matchStatus == liveActitivites.Penalties
      )
    }

    "generate a fulltime time live activity UPDATE payload without a result" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "FT", result = false)

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)

      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .matchStatus == liveActitivites.FullTime
      )
      result must contain((payload: LiveActivityPayload) =>
        payload.eventType == UpdateLiveActivityEvent
      )

    }

    "generate a fulltime time live activity UPDATE payload with a result" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "FT", result = true)

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)

      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .matchStatus == liveActitivites.FullTime
      )
      result must contain((payload: LiveActivityPayload) =>
        payload.eventType == UpdateLiveActivityEvent
      )

    }

    "NOT generate a fulltime time live activity END payload only with result" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "FT", result = true, liveMatch = true)

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .matchStatus == liveActitivites.FullTime
      )
      result must contain((payload: LiveActivityPayload) =>
        payload.eventType == UpdateLiveActivityEvent
      )
    }

    "generate a live activity END payload when there is a result AND the match is no longer live" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "FT", result = true, liveMatch = false)

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .matchStatus == liveActitivites.FullTime
      )
      result must contain((payload: LiveActivityPayload) =>
        payload.eventType == EndLiveActivityEvent
      )
    }

    // todo - check end broadcast states are shown
    "generate a abandoned live activity END payload" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDay.copy(matchStatus = "Abandoned", result = false)

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .matchStatus == liveActitivites.Abandoned
      )
      result must contain((payload: LiveActivityPayload) =>
        payload.eventType == EndLiveActivityEvent
      )
    }
  }

  "A LiveActivity EventConsumer handles PA trigger events and" should {

    "generate a goal payload" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDayLA.copy(date = ZonedDateTime.now().plusHours(1))

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)

      result must contain((payload: LiveActivityPayload) =>
        payload.eventType == UpdateLiveActivityEvent
      )
      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .awayTeam.score mustEqual(1)
      )
    }

    "generate a red card payload" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDayLA.copy(date = ZonedDateTime.now().plusHours(1))

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)
      result must contain((payload: LiveActivityPayload) =>
        payload.eventType == UpdateLiveActivityEvent
      )
      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .homeTeam.redCards mustEqual(1)
      )
    }

    "generate a penalty kick payload" in new MatchEventsContext {
      override def matchDayLA: MatchDay =
        super.matchDayLA.copy(date = ZonedDateTime.now().plusHours(1))

      val result: List[LiveActivityPayload] =
        eventConsumerLiveActivities.eventsToLiveActivityPayload(matchDataLA)

      result must contain((payload: LiveActivityPayload) =>
        payload.eventType == UpdateLiveActivityEvent
      )
      result must contain((payload: LiveActivityPayload) =>
        payload.broadcastContentStateData.get
          .asInstanceOf[FootballMatchContentState]
          .homeTeam.penaltyScore
          .exists(_.asInstanceOf[PenaltyShootoutState].scored == 1)
      )
    }
  }

  trait MatchEventsContext extends Scope {
    val matchStatusNotificationBuilder = new MatchStatusNotificationBuilder(
      "https://mobile.guardianapis.com"
    )
    val eventConsumer = new EventConsumer(matchStatusNotificationBuilder)

    val matchStatusLiveActivityPayloadBuilder =
      new MatchStatusLiveActivityPayloadBuilder()
    val eventConsumerLiveActivities = new LiveActivityEventConsumer(
      matchStatusLiveActivityPayloadBuilder
    )

    def loadFile(file: String): String = {
      val stream = this.getClass.getClassLoader.getResourceAsStream(file)
      Source.fromInputStream(stream).mkString
    }

    def rawEvents: List[MatchEvent] =
      Parser.parseMatchEvents(loadFile("match-event-feed.xml")).get.events

    def matchDay: MatchDay = Parser.parseMatchDay(loadFile("20170811.xml")).head

    def events: List[MatchEvent] =
      new SyntheticMatchEventGenerator(() => ZonedDateTime.now()).generate(
        rawEvents,
        "4011135",
        matchDay
      )

    def matchData = MatchDataWithArticle(
      matchDay,
      events,
      Some(
        "football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live"
      )
    )

    // live activity (we need a complete feed with penalties to test all live activity updates including ending the activity)
    def rawEventsLA: List[MatchEvent] = Parser
      .parseMatchEvents(loadFile("match-event-feed-penalties.xml"))
      .get
      .events

    def matchDayLA: MatchDay =
      Parser.parseMatchDay(loadFile("4484328-penalties.xml")).head

    def eventsLA: List[MatchEvent] =
      new SyntheticMatchEventGenerator(() => ZonedDateTime.now())
        .generate(rawEventsLA, "4484328", matchDayLA)

    def matchDataLA = MatchDataWithArticle(
      matchDayLA,
      eventsLA,
      Some(
        "football/live/2025/feb/11/exeter-city-v-nottingham-forest-juventus-v-psv-and-more-football-live"
      )
    )
  }

}
