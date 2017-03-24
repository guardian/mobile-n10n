package notification.models.azure

import java.net.URI
import java.util.UUID

import models.Importance.{Major, Minor}
import models.Link.Internal
import models.{GITContent, Topic}
import models.TopicTypes.{LiveNotification, TagSeries}
import models.elections.{CandidateResults, ElectionResults}
import notification.models.Push
import notification.models.android._
import notification.services.Configuration
import notification.services.azure.{GCMPushConverter, PlatformUriTypes}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class AndroidNotificationSpec extends Specification with Mockito {

  "A breaking news" should {
    "serialize to map" in new BreakingNewsScope {
      notification.payload shouldEqual expected
    }
  }

  "A content notification" should {
    "serialize to map" in new ContentNotificationScope {
      notification.payload shouldEqual expected
    }
  }

  "A goal alert notification" should {
    "serialize to map" in new GoalAlertNotificationScope {
      notification.payload shouldEqual expected
    }
  }

  "An election notification" should {
    "serialize to map" in new ElectionNotificationScope {
      converter.toRawPush(push).get.body.data shouldEqual expected
    }
    "Have importance=Minor for minor notifications" in new MinorElectionNotificationScope {
      converter.toRawPush(minorPush).get.body.data shouldEqual minorExpected
    }
  }

  "A live event notification" should {
    "serialize to map" in new LiveEventNotificationScope {
      converter.toRawPush(push).get.body.data shouldEqual expected
    }
  }

  "A football match status notification" should {
    "serialize to map" in new FootballMatchStatusNotificationScope {
      converter.toRawPush(push).get.body.data shouldEqual expected
    }
  }

  trait NotificationScope extends Scope {
    val converter = new GCMPushConverter(mock[Configuration])
    def expected: Map[String, String]
  }

  trait BreakingNewsScope extends NotificationScope {
    val notification = BreakingNewsNotification(
      id = UUID.fromString("30aac5f5-34bb-4a88-8b69-97f995a4907b"),
      title = "The Guardian",
      message = "Mali hotel attack: UN counts 27 bodies as hostage situation ends",
      thumbnailUrl = Some(new URI("http://media.guim.co.uk/09951387fda453719fe1fee3e5dcea4efa05e4fa/0_181_3596_2160/140.jpg")),
      link = new URI("http://mobile-apps.guardianapis.com/items/world/live/2015/nov/20/mali-hotel-attack-gunmen-take-hostages-in-bamako-live-updates"),
      uriType = PlatformUriTypes.Item,
      uri = "http://mobile-apps.guardianapis.com/items/world/live/2015/nov/20/mali-hotel-attack-gunmen-take-hostages-in-bamako-live-updates-uri",
      imageUrl = Some(new URI("https://mobile.guardianapis.com/img/media/a5fb401022d09b2f624a0cc0484c563fd1b6ad93/" +
        "0_308_4607_2764/master/4607.jpg/6ad3110822bdb2d1d7e8034bcef5dccf?width=800&height=-&quality=85")),
      debug = true,
      section = None,
      edition = None,
      keyword = None,
      editions = Set.empty,
      ticker = ""
    )

    val expected = Map(
      "uniqueIdentifier" -> "30aac5f5-34bb-4a88-8b69-97f995a4907b",
      "editions" -> "",
      "uri" -> "http://mobile-apps.guardianapis.com/items/world/live/2015/nov/20/mali-hotel-attack-gunmen-take-hostages-in-bamako-live-updates-uri",
      "debug" -> "true",
      "uriType" -> "item",
      "notificationType" -> "news",
      "link" -> "http://mobile-apps.guardianapis.com/items/world/live/2015/nov/20/mali-hotel-attack-gunmen-take-hostages-in-bamako-live-updates",
      "message" -> "Mali hotel attack: UN counts 27 bodies as hostage situation ends",
      "title" -> "The Guardian",
      "type" -> "custom",
      "ticker" -> "",
      "imageUrl" -> "https://mobile.guardianapis.com/img/media/a5fb401022d09b2f624a0cc0484c563fd1b6ad93/0_308_4607_2764/master/4607.jpg/6ad3110822bdb2d1d7e8034bcef5dccf?width=800&height=-&quality=85",
      "thumbnailUrl" -> "http://media.guim.co.uk/09951387fda453719fe1fee3e5dcea4efa05e4fa/0_181_3596_2160/140.jpg"
    )
  }

  trait ContentNotificationScope extends NotificationScope {
    val notification = ContentNotification(
      id = UUID.fromString("c8bd6aaa-072f-4593-a38b-322f3ecd6bd3"),
      title = "Follow",
      message = "Which countries are doing the most to stop dangerous global warming?",
      thumbnailUrl = Some(new URI("http://media.guim.co.uk/a07334e4ed5d13d3ecf4c1ac21145f7f4a099f18/127_0_3372_2023/140.jpg")),
      uri = new URI("test-uri"),
      uriType = PlatformUriTypes.Item,
      ticker = "",
      link = new URI("http://mobile-apps.guardianapis.com/items/environment/ng-interactive/2015/oct/16/" +
        "which-countries-are-doing-the-most-to-stop-dangerous-global-warming"),
      topics = Set("tag-series//environment/series/keep-it-in-the-ground"),
      debug = true
    )

    val expected = Map(
      "topics" -> "tag-series//environment/series/keep-it-in-the-ground",
      "uniqueIdentifier" -> "c8bd6aaa-072f-4593-a38b-322f3ecd6bd3",
      "uri" -> "test-uri",
      "debug" -> "true",
      "uriType" -> "item",
      "link" -> "http://mobile-apps.guardianapis.com/items/environment/ng-interactive/2015/oct/16/which-countries-are-doing-the-most-to-stop-dangerous-global-warming",
      "message" -> "Which countries are doing the most to stop dangerous global warming?",
      "title" -> "Follow",
      "type" -> "custom",
      "ticker" -> "",
      "thumbnailUrl" -> "http://media.guim.co.uk/a07334e4ed5d13d3ecf4c1ac21145f7f4a099f18/127_0_3372_2023/140.jpg"
    )
  }

  trait GoalAlertNotificationScope extends NotificationScope {
    val notification = GoalAlertNotification(
      id = UUID.fromString("3e0bc788-a27c-4864-bb71-77a80aadcce4"),
      awayTeamName = "Watford",
      awayTeamScore = 1,
      homeTeamName = "Leicester",
      homeTeamScore = 2,
      scoringTeamName = "Watford",
      scorerName = "Deeney",
      goalMins = 75,
      otherTeamName = "Leicester",
      matchId = "3833380",
      mapiUrl = new URI("http://football.mobile-apps.guardianapis.com/match-info/3833380"),
      uri = new URI("http://football.mobile-apps.guardianapis.com/match-info/3833380"),
      uriType = PlatformUriTypes.FootballMatch,
      debug = true
    )

    val expected =  Map(
      "AWAY_TEAM_SCORE" -> "1",
      "uniqueIdentifier" -> "3e0bc788-a27c-4864-bb71-77a80aadcce4",
      "GOAL_MINS" -> "75",
      "HOME_TEAM_SCORE" -> "2",
      "uri" -> "http://football.mobile-apps.guardianapis.com/match-info/3833380",
      "OTHER_TEAM_NAME" -> "Leicester",
      "SCORING_TEAM_NAME" -> "Watford",
      "AWAY_TEAM_NAME" -> "Watford",
      "debug" -> "true",
      "uriType" -> "football-match",
      "SCORER_NAME" -> "Deeney",
      "type" -> "goalAlert",
      "HOME_TEAM_NAME" -> "Leicester",
      "mapiUrl" -> "http://football.mobile-apps.guardianapis.com/match-info/3833380",
      "matchId" -> "3833380"
    )
  }

  trait ElectionNotificationScope extends NotificationScope {
    val notification = models.ElectionNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
      message = "• 35 states called, 5 swing states (OH, PA, NV, CO, FL)\n• Popular vote: Clinton 52%, Trump 43% with 42% precincts reporting",
      shortMessage = Some("this is the short message"),
      expandedMessage = Some("this is the expanded message"),
      sender = "some-sender",
      title = "Live election results",
      importance = Major,
      link = Internal("world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray", Some("https://gu.com/p/4p7xt"), GITContent),
      resultsLink = Internal("world/2016/oct/26/canada-women-un-ranking-discrimination-justin-trudeau", Some("https://gu.com/p/5982v"), GITContent),
      results = ElectionResults(List(
        CandidateResults(
          name = "Clinton",
          states = List.empty,
          electoralVotes = 220,
          popularVotes = 5000000,
          avatar = Some(new URI("http://e4775a29.ngrok.io/clinton-neutral.png")),
          color = "#005689"
        ),
        CandidateResults(
          name = "Trump",
          states = List.empty,
          electoralVotes = 133,
          popularVotes = 5000000,
          avatar = Some(new URI("http://e4775a29.ngrok.io/trump-neutral.png")),
          color = "#d61d00"
        )
      )),
      topic = Set.empty
    )

    val push = Push(notification, Left(Set(Topic(TagSeries, "series-a"), Topic(TagSeries, "series-b"))))

    val expected =  Map(
      "uniqueIdentifier" -> "068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7",
      "debug" -> "false",
      "type" -> "liveElections",
      "candidates.length" -> "2",
      "candidates[0].name" -> "Clinton",
      "candidates[0].electoralVotes" -> "220",
      "candidates[0].color" -> "#005689",
      "candidates[0].avatar" -> "http://e4775a29.ngrok.io/clinton-neutral.png",
      "candidates[1].name" -> "Trump",
      "candidates[1].electoralVotes" -> "133",
      "candidates[1].color" -> "#d61d00",
      "candidates[1].avatar" -> "http://e4775a29.ngrok.io/trump-neutral.png",
      "electoralCollegeSize" -> "538",
      "link" -> "x-gu://www.guardian.co.uk/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
      "resultsLink" -> "x-gu://www.guardian.co.uk/world/2016/oct/26/canada-women-un-ranking-discrimination-justin-trudeau",
      "title" -> "Live election results",
      "importance" -> "Major",
      "expandedMessage" -> "this is the expanded message",
      "shortMessage" -> "this is the short message"
    )
  }

  trait MinorElectionNotificationScope extends ElectionNotificationScope {
    val minorNotification = notification.copy(importance = Minor)
    val minorExpected = expected.updated("importance", "Minor")
    val minorPush = Push(minorNotification, Left(Set(Topic(TagSeries, "series-a"), Topic(TagSeries, "series-b"))))
  }

  trait LiveEventNotificationScope extends NotificationScope {
    val notification = models.LiveEventNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
      sender = "some-sender",
      title = "Some live event",
      message = "normal message",
      expandedMessage = Some("this is the expanded message"),
      shortMessage = Some("this is the short message"),
      importance = Major,
      link1 = Internal("world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray", Some("https://gu.com/p/4p7xt"), GITContent),
      link2 = Internal("world/2016/oct/26/canada-women-un-ranking-discrimination-justin-trudeau", Some("https://gu.com/p/5982v"), GITContent),
      imageUrl = Some(new URI("http://gu.com/some-image.png")),
      topic = Set(Topic(LiveNotification, "super-bowl-li"))
    )

    val push = Push(notification, Left(notification.topic))

    val expected =  Map(
      "uniqueIdentifier" -> "068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7",
      "debug" -> "false",
      "type" -> "superBowl",
      "link1" -> "x-gu://www.guardian.co.uk/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
      "link2" -> "x-gu://www.guardian.co.uk/world/2016/oct/26/canada-women-un-ranking-discrimination-justin-trudeau",
      "title" -> "Some live event",
      "importance" -> "Major",
      "expandedMessage" -> "this is the expanded message",
      "shortMessage" -> "this is the short message",
      "message" -> "normal message",
      "imageUrl" -> "http://gu.com/some-image.png",
      "topics" -> "live-notification//super-bowl-li"
    )
  }

  trait FootballMatchStatusNotificationScope extends NotificationScope {
    val notification = models.FootballMatchStatusNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
      sender = "some-sender",
      title = "Some live event",
      message = "normal message",
      thumbnailUrl = None,
      awayTeamName = "Burnley",
      awayTeamScore = 1,
      awayTeamMessage = "Andre Gray 90 +2:41 Pen",
      awayTeamId = "70",
      homeTeamName = "Arsenal",
      homeTeamScore = 2,
      homeTeamMessage = "Shkodran Mustafi 59\nAlexis Sanchez 90 +7:14 Pen",
      homeTeamId = "1006",
      competitionName = Some("Premier League"),
      venue = Some("Emirates Stadium"),
      matchId = "1000",
      mapiUrl = new URI("http://football.mobile-apps.guardianapis.com/match-info/3955232"),
      importance = Major,
      topic = Set.empty,
      phase = "P",
      eventId = "1000",
      debug = false
    )

    val push = Push(notification, Left(notification.topic))

    val expected =  Map(
      "type" -> "footballMatchAlert",
      "homeTeamName" -> "Arsenal",
      "homeTeamId" -> "1006",
      "homeTeamScore" -> "2",
      "homeTeamText" -> "Shkodran Mustafi 59\nAlexis Sanchez 90 +7:14 Pen",
      "awayTeamName" -> "Burnley",
      "awayTeamId" -> "70",
      "awayTeamScore" -> "1",
      "awayTeamText" -> "Andre Gray 90 +2:41 Pen",
      "currentMinute" -> "", // TODO: 90:0
      "important" -> "Major",
      "matchStatus" -> "P",
      "matchId" -> "1000",
      "mapiUrl" -> "http://football.mobile-apps.guardianapis.com/match-info/3955232",
      "uri" -> "", // TODO:
      "competitionName" -> "Premier League",
      "venue" -> "Emirates Stadium"
    )
  }
}
