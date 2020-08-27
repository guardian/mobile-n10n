package football.lib

import java.net.URI

import com.gu.mobile.notifications.client.models._
import com.gu.mobile.notifications.client.models.Importance.{Major, Minor}
import com.gu.mobile.notifications.client.models.TopicTypes.{FootballMatch, FootballTeam}
import football.models._
import football.notificationbuilders.MatchStatusNotificationBuilder
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pa.{MatchDay, MatchEvent, Parser}

import scala.io.Source

class EventConsumerSpec(implicit ev: ExecutionEnv) extends Specification with Mockito {
  "An EventConsumer" should {
    "generate a kick-off notification" in new MatchEventsContext {
      override def matchDay: MatchDay = super.matchDay.copy(matchStatus = "KO")

      val result: List[NotificationPayload] = eventConsumer.eventsToNotifications(matchData)

      val expectedNotification = FootballMatchStatusPayload(
        title = "Kick-off!",
        message = "Arsenal 0-0 Leicester (1st)",
        thumbnailUrl = None,
        sender = "mobile-notifications-football-lambda",
        awayTeamName = "Leicester",
        awayTeamScore = 0,
        awayTeamMessage = " ",
        awayTeamId = "29",
        homeTeamName = "Arsenal",
        homeTeamScore = 0,
        homeTeamMessage = " ",
        homeTeamId = "1006",
        competitionName = Some("Premier League 17/18"),
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
        eventId = "7e730fbe-b013-3a0e-89cb-12b46260d7be",
        debug = false
      )

      result should contain(expectedNotification)
    }

    "generate half-time notification" in new MatchEventsContext {
      override def matchDay: MatchDay = super.matchDay.copy(matchStatus = "HT")

      val result: List[NotificationPayload] = eventConsumer.eventsToNotifications(matchData)

      val expectedNotification = FootballMatchStatusPayload(
        title = "Half-time",
        message = "Arsenal 3-0 Leicester (HT)",
        thumbnailUrl = None,
        sender = "mobile-notifications-football-lambda",
        awayTeamName = "Leicester",
        awayTeamScore = 0,
        awayTeamMessage = " ",
        awayTeamId = "29",
        homeTeamName = "Arsenal",
        homeTeamScore = 3,
        homeTeamMessage = "Henrikh Mkhitaryan 10'\nSofiane Hanni 32'\nRed card: Carl Jenkinson 106'\nMarcus Rashford 107'\nRed card: Henrikh Mkhitaryan 114'",
        homeTeamId = "1006",
        competitionName = Some("Premier League 17/18"),
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
        matchStatus = "HT",
        eventId = "bb346058-64d0-3ab1-9016-ea19d90837f0",
        debug = false
      )

      result should contain(expectedNotification)
    }

    "generate second half start notification" in new MatchEventsContext {
      // 23572566 is the first event of the second half
      override def rawEvents: List[MatchEvent] = super.rawEvents.takeWhile(!_.id.contains("23572566"))
      override def matchDay: MatchDay = super.matchDay.copy(matchStatus = "SHS")

      val result: List[NotificationPayload] = eventConsumer.eventsToNotifications(matchData)

      val expectedNotification = FootballMatchStatusPayload(
        title = "Second-half start",
        message = "Arsenal 2-0 Leicester (2nd)",
        thumbnailUrl = None,
        sender = "mobile-notifications-football-lambda",
        awayTeamName = "Leicester",
        awayTeamScore = 0,
        awayTeamMessage = " ",
        awayTeamId = "29",
        homeTeamName = "Arsenal",
        homeTeamScore = 2,
        homeTeamMessage = "Henrikh Mkhitaryan 10'\nSofiane Hanni 32'",
        homeTeamId = "1006",
        competitionName = Some("Premier League 17/18"),
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
        matchStatus = "2nd",
        eventId = "a45dfca1-ead9-3d8c-bf83-c4966a737b05",
        debug = false
      )

      result should contain(expectedNotification)
    }

    "generate full time notification" in new MatchEventsContext {
      override def matchDay: MatchDay = super.matchDay.copy(matchStatus = "FT", result = true)

      val result: List[NotificationPayload] = eventConsumer.eventsToNotifications(matchData)

      val expectedNotification = FootballMatchStatusPayload(
        title = "Full-Time",
        message = "Arsenal 3-0 Leicester (FT)",
        thumbnailUrl = None,
        sender = "mobile-notifications-football-lambda",
        awayTeamName = "Leicester",
        awayTeamScore = 0,
        awayTeamMessage = " ",
        awayTeamId = "29",
        homeTeamName = "Arsenal",
        homeTeamScore = 3,
        homeTeamMessage = "Henrikh Mkhitaryan 10'\nSofiane Hanni 32'\nRed card: Carl Jenkinson 106'\nMarcus Rashford 107'\nRed card: Henrikh Mkhitaryan 114'",
        homeTeamId = "1006",
        competitionName = Some("Premier League 17/18"),
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
        matchStatus = "FT",
        eventId = "d59c9939-8199-3b8b-ad63-16aa020c1a73",
        debug = false
      )

      result should contain(expectedNotification)
    }

    "generate goal notifications from FootballMatchStatusPayload" in new MatchEventsContext {
      override def matchDay: MatchDay = super.matchDay.copy(matchStatus = "KO", result = true)

      val result: List[NotificationPayload] = eventConsumer.eventsToNotifications(matchData)

      val expectedNotification = FootballMatchStatusPayload(
        title = "Goal!",
        message = "Arsenal 1-0 Leicester (1st)\nHenrikh Mkhitaryan 10min",
        thumbnailUrl = None,
        sender = "mobile-notifications-football-lambda",
        awayTeamName = "Leicester",
        awayTeamScore = 0,
        awayTeamMessage = " ",
        awayTeamId = "29",
        homeTeamName = "Arsenal",
        homeTeamScore = 1,
        homeTeamMessage = "Henrikh Mkhitaryan 10'",
        homeTeamId = "1006",
        competitionName = Some("Premier League 17/18"),
        venue = Some("Emirates Stadium"),
        matchId = "4011135",
        matchInfoUri = new URI("https://mobile.guardianapis.com/sport/football/matches/4011135"),
        articleUri = Some(new URI("https://mobile.guardianapis.com/items/football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live")),
        importance = Major,
        topic = List(
          Topic(FootballTeam, "1006"),
          Topic(FootballTeam, "29"),
          Topic(FootballMatch, "4011135")
        ),
        matchStatus = "1st",
        eventId = "1c8d67f9-0f32-342a-8543-aa3e21ee7da4",
        debug = false
      )

      result should contain(expectedNotification)
    }
    "generate red card notifications from FootballMatchStatusPayload" in new MatchEventsContext {
      override def matchDay: MatchDay = super.matchDay.copy(matchStatus = "KO", result = true)
      val result: List[NotificationPayload] = eventConsumer.eventsToNotifications(matchData)

      val expectedNotification = FootballMatchStatusPayload(
        "Red card",
        "Arsenal 3-0 Leicester (1st)\nHenrikh Mkhitaryan (Arsenal) 114min",
        None,
        "mobile-notifications-football-lambda",
        "Leicester",
        0,
        " ",
        "29",
        "Arsenal",
        3,
        "Red card: Henrikh Mkhitaryan 114'\nHenrikh Mkhitaryan 10'\nSofiane Hanni 32'\nRed card: Carl Jenkinson 106'\nMarcus Rashford 107'",
        "1006",
        Some("Premier League 17/18"),
        Some("Emirates Stadium"),
        "4011135",
        new URI("https://mobile.guardianapis.com/sport/football/matches/4011135"),
        Some(new URI("https://mobile.guardianapis.com/items/football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live")),
        Minor,
        List(Topic(FootballTeam,"1006"), Topic(FootballTeam,"29"), Topic(FootballMatch,"4011135")),
        "1st",
        "7c92d6ca-9f20-398f-9510-eb4c179fb5ae",
        false)

      result should contain(expectedNotification)
    }
  }

  trait MatchEventsContext extends Scope {
    val matchStatusNotificationBuilder = new MatchStatusNotificationBuilder("https://mobile.guardianapis.com")
    val eventConsumer = new EventConsumer(matchStatusNotificationBuilder)

    def loadFile(file: String): String = {
      val stream = this.getClass.getClassLoader.getResourceAsStream(file)
      Source.fromInputStream(stream).mkString
    }

    def rawEvents: List[MatchEvent] = Parser.parseMatchEvents(loadFile("match-event-feed.xml")).get.events
    def matchDay: MatchDay = Parser.parseMatchDay(loadFile("20170811.xml")).head

    def events: List[MatchEvent] = new SyntheticMatchEventGenerator().generate(rawEvents, "4011135", matchDay)
    def matchData = MatchDataWithArticle(matchDay, events, Some("football/live/2017/aug/11/arsenal-v-leicester-city-premier-league-live"))
  }
}
