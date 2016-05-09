package models

import java.net.URI
import java.util.UUID

import models.GoalType.Penalty
import models.Importance.Major
import models.Link.Internal
import models.TopicTypes.{Breaking, FootballMatch, FootballTeam, TagSeries}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json

class NotificationSpec extends Specification {

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

   trait NotificationScope extends Scope {
     def notification: Notification
     def expected: String
   }

   trait BreakingNewsScope extends NotificationScope {
     val notification = BreakingNewsNotification(
       id = UUID.fromString("30aac5f5-34bb-4a88-8b69-97f995a4907b"),
       title = "The Guardian",
       message = "Mali hotel attack: UN counts 27 bodies as hostage situation ends",
       thumbnailUrl = Some(new URI("http://media.guim.co.uk/09951387fda453719fe1fee3e5dcea4efa05e4fa/0_181_3596_2160/140.jpg")),
       sender = "test",
       link = Internal("world/live/2015/nov/20/mali-hotel-attack-gunmen-take-hostages-in-bamako-live-updates"),
       imageUrl = Some(new URI("https://mobile.guardianapis.com/img/media/a5fb401022d09b2f624a0cc0484c563fd1b6ad93/" +
         "0_308_4607_2764/master/4607.jpg/6ad3110822bdb2d1d7e8034bcef5dccf?width=800&height=-&quality=85")),
       importance = Major,
       topic = Set(Topic(Breaking, "uk"))
     )

     val expected =
       """
         |{
         |  "id" : "30aac5f5-34bb-4a88-8b69-97f995a4907b",
         |  "type" : "news",
         |  "title" : "The Guardian",
         |  "message" : "Mali hotel attack: UN counts 27 bodies as hostage situation ends",
         |  "thumbnailUrl" : "http://media.guim.co.uk/09951387fda453719fe1fee3e5dcea4efa05e4fa/0_181_3596_2160/140.jpg",
         |  "sender": "test",
         |  "link" : {
         |    "contentApiId": "world/live/2015/nov/20/mali-hotel-attack-gunmen-take-hostages-in-bamako-live-updates"
         |  },
         |  "imageUrl" : "https://mobile.guardianapis.com/img/media/a5fb401022d09b2f624a0cc0484c563fd1b6ad93/0_308_4607_2764/master/4607.jpg/6ad3110822bdb2d1d7e8034bcef5dccf?width=800&height=-&quality=85",
         |  "importance" : "Major",
         |  "topic" : [ {
         |    "type" : "breaking",
         |    "name" : "uk"
         |  } ]
         |}
       """.stripMargin
   }

   trait ContentNotificationScope extends NotificationScope {
     val notification = ContentNotification(
       id = UUID.fromString("c8bd6aaa-072f-4593-a38b-322f3ecd6bd3"),
       title = "Follow",
       message = "Which countries are doing the most to stop dangerous global warming?",
       thumbnailUrl = Some(new URI("http://media.guim.co.uk/a07334e4ed5d13d3ecf4c1ac21145f7f4a099f18/127_0_3372_2023/140.jpg")),
       sender = "test",
       link = Internal("environment/ng-interactive/2015/oct/16/which-countries-are-doing-the-most-to-stop-dangerous-global-warming"),
       importance = Major,
       topic = Set(Topic(TagSeries, "environment/series/keep-it-in-the-ground"))
     )

     val expected =
       """
         |{
         |  "id" : "c8bd6aaa-072f-4593-a38b-322f3ecd6bd3",
         |  "type" : "content",
         |  "title" : "Follow",
         |  "message" : "Which countries are doing the most to stop dangerous global warming?",
         |  "thumbnailUrl" : "http://media.guim.co.uk/a07334e4ed5d13d3ecf4c1ac21145f7f4a099f18/127_0_3372_2023/140.jpg",
         |  "sender" : "test",
         |  "link" : {
         |    "contentApiId" : "environment/ng-interactive/2015/oct/16/which-countries-are-doing-the-most-to-stop-dangerous-global-warming"
         |  },
         |  "importance" : "Major",
         |  "topic" : [ {
         |    "type" : "tag-series",
         |    "name" : "environment/series/keep-it-in-the-ground"
         |  } ]
         |}
       """.stripMargin
   }

   trait GoalAlertNotificationScope extends NotificationScope {
     val notification = GoalAlertNotification(
       id = UUID.fromString("3e0bc788-a27c-4864-bb71-77a80aadcce4"),
       title = "The Guardian",
       message = "Leicester 2-1 Watford\nDeeney 75min",
       sender = "test",
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
       mapiUrl = new URI("http://football.mobile-apps.guardianapis.com/match-info/3833380"),
       importance = Major,
       topic = Set(
         Topic(FootballTeam, "29"),
         Topic(FootballTeam, "41"),
         Topic(FootballMatch, "3833380")
       ),
       addedTime = None
     )

     val expected =
       """
         |{
         |  "id" : "3e0bc788-a27c-4864-bb71-77a80aadcce4",
         |  "type" : "goal",
         |  "title" : "The Guardian",
         |  "message" : "Leicester 2-1 Watford\nDeeney 75min",
         |  "sender" : "test",
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
         |  "mapiUrl" : "http://football.mobile-apps.guardianapis.com/match-info/3833380",
         |  "importance" : "Major",
         |  "topic" : [ {
         |    "type" : "football-team",
         |    "name" : "29"
         |  }, {
         |    "type" : "football-team",
         |    "name" : "41"
         |  }, {
         |    "type" : "football-match",
         |    "name" : "3833380"
         |  } ]
         |}
       """.stripMargin
   }

 }
