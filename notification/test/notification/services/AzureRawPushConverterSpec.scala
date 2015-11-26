package notification.services

import java.util.UUID

import azure.Tag
import models.GoalType.Penalty
import models.Importance.Major
import models.Link.Internal
import models._
import models.TopicTypes.{FootballMatch, FootballTeam, TagSeries, Breaking}
import notification.models.azure
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class AzureRawPushConverterSpec extends Specification with Mockito {

  "AzureRawPushConverter.toAzure" should {
    "convert a breaking news into the azure format" in new BreakingNewsScope {
      azureRawPushConverter.toAzure(notification) shouldEqual azureNotification
    }
    "convert a content notification into the azure format" in new ContentNotificationScope {
      azureRawPushConverter.toAzure(notification) shouldEqual azureNotification
    }
    "convert a goal alert into the azure format" in new GoalAlertNotificationScope {
      azureRawPushConverter.toAzure(notification) shouldEqual azureNotification
    }
  }

  "AzureRawPushConverter.toTag" should {
    "Convert a userId into a tag" in new PushConverterScope {
      val userId = UserId(id = UUID.fromString("497f172a-9434-11e5-af4E-61a964696656"))
      val expected = Tag("user:497f172a-9434-11e5-af4e-61a964696656")
      azureRawPushConverter.toTags(Right(userId)) shouldEqual Some(Set(expected))
    }
    "Convert a topic into a tag" in new PushConverterScope {
      val topic = Topic(Breaking, "uk")
      val expected = Tag("topic:Base16:627265616b696e672f756b")
      azureRawPushConverter.toTags(Left(topic)) shouldEqual Some(Set(expected))
    }
  }

  trait PushConverterScope extends Scope {
    def configuration = {
      val c = mock[Configuration]
      c.mapiItemEndpoint returns "http://mobile-apps.guardianapis.com/items"
      c
    }

    val azureRawPushConverter = new AzureRawPushConverter(configuration)
  }

  trait BreakingNewsScope extends PushConverterScope {
    val notification = BreakingNewsNotification(
      id = UUID.fromString("30aac5f5-34bb-4a88-8b69-97f995a4907b"),
      title = "The Guardian",
      message = "Mali hotel attack: UN counts 27 bodies as hostage situation ends",
      thumbnailUrl = Some(URL("http://media.guim.co.uk/09951387fda453719fe1fee3e5dcea4efa05e4fa/0_181_3596_2160/140.jpg")),
      sender = "test",
      link = Internal("world/live/2015/nov/20/mali-hotel-attack-gunmen-take-hostages-in-bamako-live-updates"),
      imageUrl = Some(URL("https://mobile.guardianapis.com/img/media/a5fb401022d09b2f624a0cc0484c563fd1b6ad93/0_308_4607_2764/master/4607.jpg/6ad3110822bdb2d1d7e8034bcef5dccf?width=800&height=-&quality=85")),
      importance = Major,
      topic = Set(Topic(Breaking, "uk"))
    )

    val azureNotification = azure.BreakingNewsNotification(
      id = UUID.fromString("30aac5f5-34bb-4a88-8b69-97f995a4907b"),
      title = "The Guardian",
      message = "Mali hotel attack: UN counts 27 bodies as hostage situation ends",
      thumbnailUrl = Some(URL("http://media.guim.co.uk/09951387fda453719fe1fee3e5dcea4efa05e4fa/0_181_3596_2160/140.jpg")),
      link = URL("http://mobile-apps.guardianapis.com/items/world/live/2015/nov/20/mali-hotel-attack-gunmen-take-hostages-in-bamako-live-updates"),
      imageUrl = Some(URL("https://mobile.guardianapis.com/img/media/a5fb401022d09b2f624a0cc0484c563fd1b6ad93/0_308_4607_2764/master/4607.jpg/6ad3110822bdb2d1d7e8034bcef5dccf?width=800&height=-&quality=85")),
      topic = Set(Topic(Breaking, "uk"))
    )
  }

  trait ContentNotificationScope extends PushConverterScope {
    val notification = ContentNotification(
      id = UUID.fromString("c8bd6aaa-072f-4593-a38b-322f3ecd6bd3"),
      title = "Follow",
      message = "Which countries are doing the most to stop dangerous global warming?",
      thumbnailUrl = Some(URL("http://media.guim.co.uk/a07334e4ed5d13d3ecf4c1ac21145f7f4a099f18/127_0_3372_2023/140.jpg")),
      sender = "test",
      link = Internal("environment/ng-interactive/2015/oct/16/which-countries-are-doing-the-most-to-stop-dangerous-global-warming"),
      importance = Major,
      topic = Set(Topic(TagSeries, "environment/series/keep-it-in-the-ground")),
      shortUrl = "shortUrl"
    )

    val azureNotification = azure.ContentNotification(
      id = UUID.fromString("c8bd6aaa-072f-4593-a38b-322f3ecd6bd3"),
      title = "Follow",
      message = "Which countries are doing the most to stop dangerous global warming?",
      thumbnailUrl = Some(URL("http://media.guim.co.uk/a07334e4ed5d13d3ecf4c1ac21145f7f4a099f18/127_0_3372_2023/140.jpg")),
      link = URL("http://mobile-apps.guardianapis.com/items/environment/ng-interactive/2015/oct/16/which-countries-are-doing-the-most-to-stop-dangerous-global-warming"),
      topic = Set(Topic(TagSeries, "environment/series/keep-it-in-the-ground"))
    )
  }

  trait GoalAlertNotificationScope extends PushConverterScope {
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
      mapiUrl = URL("http://football.mobile-apps.guardianapis.com/match-info/3833380"),
      importance = Major,
      topic = Set(
        Topic(FootballTeam, "29"),
        Topic(FootballTeam, "41"),
        Topic(FootballMatch, "3833380")
      ),
      addedTime = None
    )

    val azureNotification = azure.GoalAlertNotification(
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
      link = URL("http://football.mobile-apps.guardianapis.com/match-info/3833380"),
      topic = Set(
        Topic(FootballTeam, "29"),
        Topic(FootballTeam, "41"),
        Topic(FootballMatch, "3833380")
      ),
      addedTime = None
    )
  }

}
