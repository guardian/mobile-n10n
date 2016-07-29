package notification.models.azure

import java.net.URI
import java.util.UUID

import azure.apns.{Alert, Body, APS}
import notification.services.Configuration
import models.Importance.Major
import models.Link.Internal
import models._
import models.TopicTypes.{Breaking, TagSeries}
import notification.models.Push
import notification.services.azure.APNSPushConverter
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class iOSNotificationSpec extends Specification with Mockito {

  "A breaking news" should {
    "serialize / deserialize to json" in new BreakingNewsScope {
      converter.toRawPush(push).body shouldEqual expected
    }
  }

  "A content notification" should {
    "serialize / deserialize to json" in new ContentNotificationScope {
      converter.toRawPush(push).body shouldEqual expected
    }
  }

  "A goal alert notification" should {
    "serialize / deserialize to json" in new GoalAlertNotificationScope {
      converter.toRawPush(push).body shouldEqual expected
    }
  }

  trait NotificationScope extends Scope {
    def push: Push
    def expected: Body
    val converter = new APNSPushConverter(mock[Configuration])
  }

  trait BreakingNewsScope extends NotificationScope {
    val notification = models.BreakingNewsNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
      `type` = NotificationType.BreakingNews,
      title  = "The Guardian",
      message = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      thumbnailUrl = Some(new URI("https://media.guim.co.uk/633850064fba4941cdac17e8f6f8de97dd736029/24_0_1800_1080/500.jpg")),
      sender = "matt.wells@guardian.co.uk",
      link = Internal("world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray", Some("https://gu.com/p/4p7xt"), GITContent),
      imageUrl = None,
      importance = Major,
      topic = Set(Topic(Breaking, "uk"), Topic(Breaking, "us"), Topic(Breaking, "au"), Topic(Breaking, "international"))
    )

    val push = Push(notification, Left(Set(Topic(Breaking, "uk"), Topic(Breaking, "us"), Topic(Breaking, "au"), Topic(Breaking, "international"))))

    val expected = Body(
      aps = APS(
        alert = Some(Alert(body = Some("French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State"))),
        category = Some("ITEM_CATEGORY")
      ),
      customProperties = Map(
        "t" -> "m",
        "notificationType" -> "news",
        "link" -> "x-gu:///p/4p7xt",
        "topics" -> "breaking/uk,breaking/us,breaking/au,breaking/international",
        "uri" -> "x-gu:///items/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
        "uriType" -> "item"
      )
    )
  }

  trait ContentNotificationScope extends NotificationScope {
    val notification = models.ContentNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
      `type` = NotificationType.Content,
      title  = "The Guardian",
      message = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      thumbnailUrl = Some(new URI("https://media.guim.co.uk/633850064fba4941cdac17e8f6f8de97dd736029/24_0_1800_1080/500.jpg")),
      sender = "matt.wells@guardian.co.uk",
      link = Internal("world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray", Some("https://gu.com/p/4p7xt"), GITContent),
      importance = Major,
      topic = Set(Topic(TagSeries, "series-a"), Topic(TagSeries, "series-b"))
    )

    val push = Push(notification, Left(Set(Topic(TagSeries, "series-a"), Topic(TagSeries, "series-b"))))

    val expected = Body(
      aps = APS(
        alert = Some(Alert(body = Some("French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State"))),
        category = Some("ITEM_CATEGORY")
      ),
      customProperties = Map(
        "t" -> "m",
        "notificationType" -> "content",
        "link" -> "x-gu:///p/4p7xt",
        "topics" -> "tag-series/series-a,tag-series/series-b",
        "uri" -> "x-gu:///items/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
        "uriType" -> "item"
      )
    )
  }

  trait GoalAlertNotificationScope extends NotificationScope {
    val topics = Set(
      Topic(TopicTypes.FootballTeam, "home-team-id"),
      Topic(TopicTypes.FootballTeam, "away-team-id"),
      Topic(TopicTypes.FootballMatch, "match-id")
    )

    val notification = models.GoalAlertNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
      `type` = NotificationType.GoalAlert,
      title  = "The Guardian",
      message = "Leicester 2-1 Watford\nDeeney 75min (o.g.)",
      thumbnailUrl = Some(new URI("http://images/test")),
      sender = "Test Sender",
      goalType = models.GoalType.Own,
      awayTeamName = "Watford",
      awayTeamScore =  1,
      homeTeamName = "Leicester",
      homeTeamScore =  2,
      scoringTeamName = "Watford",
      scorerName = "Deeney",
      goalMins = 75,
      otherTeamName = "Leicester",
      matchId = "3833380",
      mapiUrl = new URI("http://football.mobile-apps.guardianapis.com/match-info/3833380"),
      importance = Importance.Major,
      topic = topics,
      addedTime = None
    )

    val push = Push(notification, Left(Set(Topic(TagSeries, "series-a"), Topic(TagSeries, "series-b"))))

    val expected = Body(
      aps = APS(
        alert = Some(Alert(body = Some("Leicester 2-1 Watford\nDeeney 75min (o.g.)"))),
        category = None
      ),
      customProperties = Map(
        "t" -> "g",
        "uri" -> "x-gu:///match-info/3833380",
        "uriType" -> "football-match"
      )
    )
  }

}

