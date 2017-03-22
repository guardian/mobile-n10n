package notification.models.azure

import java.net.URI
import java.util.UUID

import models.GoalType.Penalty
import models.Importance.Major
import models.Link.Internal
import models.{GITContent, Topic}
import models.TopicTypes.{Breaking, FootballMatch, FootballTeam, TagSeries}
import models.elections.ElectionResults
import notification.models.Push
import notification.models.wns._
import notification.services.Configuration
import notification.services.azure.WNSPushConverter
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json

class WNSNotificationSpec extends Specification with Mockito {

  "A breaking news" should {
    "serialize / deserialize to json" in new BreakingNewsScope {
      Json.toJson(notification) shouldEqual Json.parse(expected)
    }
  }

  "A content notification" should {
    "serialize / deserialize to json" in new ContentNotificationScope {
      Json.toJson(notification) shouldEqual Json.parse(expected)
    }
  }

  "A goal alert notification" should {
    "serialize / deserialize to json" in new GoalAlertNotificationScope {
      Json.toJson(notification) shouldEqual Json.parse(expected)
    }
  }

  "An election notification" should {
    "serialize / deserialize to json" in new ElectionNotificationScope {
      converter.toRawPush(push).map(raw => Json.parse(raw.body)) should beSome(Json.parse(expected))
    }
  }

  trait NotificationScope extends Scope {
    def expected: String
    val converter = new WNSPushConverter(mock[Configuration])
  }

  trait BreakingNewsScope extends NotificationScope {
    val notification = BreakingNewsNotification(
      id = UUID.fromString("30aac5f5-34bb-4a88-8b69-97f995a4907b"),
      title = "The Guardian",
      message = "Mali hotel attack: UN counts 27 bodies as hostage situation ends",
      thumbnailUrl = Some(new URI("http://media.guim.co.uk/09951387fda453719fe1fee3e5dcea4efa05e4fa/0_181_3596_2160/140.jpg")),
      link = new URI("http://mobile-apps.guardianapis.com/items/world/live/2015/nov/20/mali-hotel-attack-gunmen-take-hostages-in-bamako-live-updates"),
      imageUrl = Some(new URI("https://mobile.guardianapis.com/img/media/a5fb401022d09b2f624a0cc0484c563fd1b6ad93/" +
        "0_308_4607_2764/master/4607.jpg/6ad3110822bdb2d1d7e8034bcef5dccf?width=800&height=-&quality=85")),
      topic = Set(Topic(Breaking, "uk")),
      debug = true
    )

    val expected =
      """
        |{
        |  "id" : "30aac5f5-34bb-4a88-8b69-97f995a4907b",
        |  "type" : "news",
        |  "title" : "The Guardian",
        |  "message" : "Mali hotel attack: UN counts 27 bodies as hostage situation ends",
        |  "thumbnailUrl" : "http://media.guim.co.uk/09951387fda453719fe1fee3e5dcea4efa05e4fa/0_181_3596_2160/140.jpg",
        |  "link" : "http://mobile-apps.guardianapis.com/items/world/live/2015/nov/20/mali-hotel-attack-gunmen-take-hostages-in-bamako-live-updates",
        |  "imageUrl" : "https://mobile.guardianapis.com/img/media/a5fb401022d09b2f624a0cc0484c563fd1b6ad93/0_308_4607_2764/master/4607.jpg/6ad3110822bdb2d1d7e8034bcef5dccf?width=800&height=-&quality=85",
        |  "topic" : [ {
        |    "type" : "breaking",
        |    "name" : "uk"
        |  } ],
        |  "debug": true
        |}
      """.stripMargin
  }

  trait ContentNotificationScope extends NotificationScope {
    val notification = ContentNotification(
      id = UUID.fromString("c8bd6aaa-072f-4593-a38b-322f3ecd6bd3"),
      title = "Follow",
      message = "Which countries are doing the most to stop dangerous global warming?",
      thumbnailUrl = Some(new URI("http://media.guim.co.uk/a07334e4ed5d13d3ecf4c1ac21145f7f4a099f18/127_0_3372_2023/140.jpg")),
      link = new URI("http://mobile-apps.guardianapis.com/items/environment/ng-interactive/2015/oct/16/" +
        "which-countries-are-doing-the-most-to-stop-dangerous-global-warming"),
      topic = Set(Topic(TagSeries, "environment/series/keep-it-in-the-ground")),
      debug = true
    )

    val expected =
      """
        |{
        |  "id" : "c8bd6aaa-072f-4593-a38b-322f3ecd6bd3",
        |  "type" : "content",
        |  "title" : "Follow",
        |  "message" : "Which countries are doing the most to stop dangerous global warming?",
        |  "thumbnailUrl" : "http://media.guim.co.uk/a07334e4ed5d13d3ecf4c1ac21145f7f4a099f18/127_0_3372_2023/140.jpg",
        |  "link" : "http://mobile-apps.guardianapis.com/items/environment/ng-interactive/2015/oct/16/which-countries-are-doing-the-most-to-stop-dangerous-global-warming",
        |  "topic" : [ {
        |    "type" : "tag-series",
        |    "name" : "environment/series/keep-it-in-the-ground"
        |  } ],
        |  "debug": true
        |}
      """.stripMargin
  }

  trait GoalAlertNotificationScope extends NotificationScope {
    val notification = GoalAlertNotification(
      id = UUID.fromString("3e0bc788-a27c-4864-bb71-77a80aadcce4"),
      title = "The Guardian",
      message = "Leicester 2-1 Watford\nDeeney 75min",
      goalType = Penalty,
      awayTeamName = "Watford",
      awayTeamScore = 1,
      homeTeamName = "Leicester",
      homeTeamScore = 2,
      scoringTeamName = "Watford",
      scorerName = "Deeney",
      goalMins = 75,
      otherTeamName = "Leicester",
      matchId = "3833380",
      link = new URI("http://football.mobile-apps.guardianapis.com/match-info/3833380"),
      topic = Set(
        Topic(FootballTeam, "29"),
        Topic(FootballTeam, "41"),
        Topic(FootballMatch, "3833380")
      ),
      addedTime = None,
      debug = true
    )

    val expected =
      """
        |{
        |  "id" : "3e0bc788-a27c-4864-bb71-77a80aadcce4",
        |  "type" : "goal",
        |  "title" : "The Guardian",
        |  "message" : "Leicester 2-1 Watford\nDeeney 75min",
        |  "goalType" : "Penalty",
        |  "awayTeamName" : "Watford",
        |  "awayTeamScore" : 1,
        |  "homeTeamName" : "Leicester",
        |  "homeTeamScore" : 2,
        |  "scoringTeamName" : "Watford",
        |  "scorerName" : "Deeney",
        |  "goalMins" : 75,
        |  "otherTeamName" : "Leicester",
        |  "matchId" : "3833380",
        |  "link" : "http://football.mobile-apps.guardianapis.com/match-info/3833380",
        |  "topic" : [ {
        |    "type" : "football-team",
        |    "name" : "29"
        |  }, {
        |    "type" : "football-team",
        |    "name" : "41"
        |  }, {
        |    "type" : "football-match",
        |    "name" : "3833380"
        |  } ],
        |  "debug": true
        |}
      """.stripMargin
  }

  trait ElectionNotificationScope extends NotificationScope {
    val notification = models.ElectionNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
      message = "test",
      shortMessage = Some("this is the short message"),
      expandedMessage = Some("this is the expanded message"),
      sender = "some-sender",
      title = "some-title",
      importance = Major,
      link = Internal("world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray", Some("https://gu.com/p/4p7xt"), GITContent),
      resultsLink = Internal("us", Some("https://gu.com/p/2zzz"), GITContent),
      results = ElectionResults(List.empty),
      topic = Set.empty
    )

    val push = Push(notification, Left(Set(Topic(TagSeries, "series-a"), Topic(TagSeries, "series-b"))))

    val expected =
      """
        |{
        |  "id": "068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7",
        |  "type": "news",
        |  "title": "test",
        |  "message": "test",
        |  "link": "null/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
        |  "topic": [],
        |  "debug": false
        |}
      """.stripMargin
  }

}
