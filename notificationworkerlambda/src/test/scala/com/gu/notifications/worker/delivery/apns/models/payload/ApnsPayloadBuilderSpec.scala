package com.gu.notifications.worker.delivery.apns.models.payload

import java.net.URI
import java.util.UUID

import com.google.gson.JsonParser
import com.gu.notifications.worker.delivery.apns.models.ApnsConfig
import models.Importance.Major
import models.Link.Internal
import models.TopicTypes.{Breaking, TagSeries}
import models.{GITContent, Notification, NotificationType, Topic}
import org.specs2.matcher.Matchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json

class ApnsPayloadBuilderSpec extends Specification with Matchers {

  "ApnsPayload" should {
    "generate correct payload for Breaking News notification" in new BreakingNewsScope {
      checkPayload()
    }
    "generate correct payload for Breaking News notification with no thumbnail" in new BreakingNewsScopeNoThumbnail {
      checkPayload()
    }
    "generate correct payload for Breaking News notification with no image" in new BreakingNewsScopeNoImage {
      checkPayload()
    }
    "generate correct payload for Content notification" in new ContentNotificationScope {
      checkPayload()
    }
    "generate correct payload for Match status notification" in new MatchStatusNotificationScope {
      checkPayload()
    }
    "generate correct payload for Newsstand notification" in new NewsstandNotificationScope {
      checkPayload()
    }
    "generate correct payload for Edition notification" in new EditionsNotificationScope {
      checkPayload()
    }
  }

  trait NotificationScope extends Scope {
    def notification: Notification
    def expected: Option[String]

    private def expectedTrimmedJson = expected.map(s => new JsonParser().parse(s).toString)
    def checkPayload() = {
      val dummyConfig = new ApnsConfig(
        teamId = "",
        bundleId = "",
        keyId = "",
        certificate = "",
        mapiBaseUrl = "https://mobile.guardianapis.com"
      )
      val generatedJson = new ApnsPayloadBuilder(dummyConfig)
        .apply(notification)
        .map(_.jsonString)
        .map(Json.parse)

      val expectedJson = expectedTrimmedJson.map(Json.parse)

       generatedJson should beEqualTo(expectedJson)
    }
  }

  trait BreakingNewsScope extends NotificationScope {
    val notification = models.BreakingNewsNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
      `type` = NotificationType.BreakingNews,
      title  = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      message = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      thumbnailUrl = Some(new URI("https://media.guim.co.uk/633850064fba4941cdac17e8f6f8de97dd736029/24_0_1800_1080/500.jpg")),
      sender = "matt.wells@guardian.co.uk",
      link = Internal("world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray", Some("https://gu.com/p/4p7xt"), GITContent),
      imageUrl = Some(new URI("https://media.guim.co.uk/633850064fba4941cdac17e8f6f8de97dd736029/24_0_1800_1080/500-image-url.jpg")),
      importance = Major,
      topic = List(Topic(Breaking, "uk"), Topic(Breaking, "us"), Topic(Breaking, "au"), Topic(Breaking, "international")),
      dryRun = None
    )

    val expected = Some(
      """{
        |   "t":"m",
        |   "aps":{
        |      "alert":{
        |         "body":"French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State"
        |      },
        |      "sound":"default",
        |      "category":"ITEM_CATEGORY",
        |      "content-available":1,
        |      "mutable-content":1
        |   },
        |   "provider":"Guardian",
        |   "topics":"breaking/uk,breaking/us,breaking/au,breaking/international",
        |   "uriType":"item",
        |   "imageUrl":"https://media.guim.co.uk/633850064fba4941cdac17e8f6f8de97dd736029/24_0_1800_1080/500.jpg",
        |   "link":"https://mobile.guardianapis.com/items/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
        |   "notificationType":"news",
        |   "uri":"https://www.theguardian.com/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
        |   "uniqueIdentifier":"068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"
        |}""".stripMargin
    )
  }

  trait BreakingNewsScopeNoThumbnail extends NotificationScope {
    val notification = models.BreakingNewsNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
      `type` = NotificationType.BreakingNews,
      title  = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      message = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      thumbnailUrl = None,
      sender = "matt.wells@guardian.co.uk",
      link = Internal("world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray", Some("https://gu.com/p/4p7xt"), GITContent),
      imageUrl = Some(new URI("https://media.guim.co.uk/633850064fba4941cdac17e8f6f8de97dd736029/24_0_1800_1080/500-image-url.jpg")),
      importance = Major,
      topic = List(Topic(Breaking, "uk"), Topic(Breaking, "us"), Topic(Breaking, "au"), Topic(Breaking, "international")),
      dryRun = None
    )

    val expected = Some(
      """{
        |   "t":"m",
        |   "aps":{
        |      "alert":{
        |         "body":"French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State"
        |      },
        |      "sound":"default",
        |      "category":"ITEM_CATEGORY",
        |      "content-available":1,
        |      "mutable-content":1
        |   },
        |   "provider":"Guardian",
        |   "topics":"breaking/uk,breaking/us,breaking/au,breaking/international",
        |   "uriType":"item",
        |   "imageUrl":"https://media.guim.co.uk/633850064fba4941cdac17e8f6f8de97dd736029/24_0_1800_1080/500-image-url.jpg",
        |   "link":"https://mobile.guardianapis.com/items/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
        |   "notificationType":"news",
        |   "uri":"https://www.theguardian.com/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
        |   "uniqueIdentifier":"068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"
        |}""".stripMargin
    )
  }

  trait BreakingNewsScopeNoImage extends NotificationScope {
    val notification = models.BreakingNewsNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
      `type` = NotificationType.BreakingNews,
      title  = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      message = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      thumbnailUrl = None,
      sender = "matt.wells@guardian.co.uk",
      link = Internal("world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray", Some("https://gu.com/p/4p7xt"), GITContent),
      imageUrl = None,
      importance = Major,
      topic = List(Topic(Breaking, "uk"), Topic(Breaking, "us"), Topic(Breaking, "au"), Topic(Breaking, "international")),
      dryRun = None
    )

    val expected = Some(
      """{
        |   "t":"m",
        |   "aps":{
        |      "alert":{
        |         "body":"French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State"
        |      },
        |      "sound":"default",
        |      "category":"ITEM_CATEGORY",
        |      "content-available":1,
        |      "mutable-content":1
        |   },
        |   "provider":"Guardian",
        |   "topics":"breaking/uk,breaking/us,breaking/au,breaking/international",
        |   "uriType":"item",
        |   "link":"https://mobile.guardianapis.com/items/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
        |   "notificationType":"news",
        |   "uri":"https://www.theguardian.com/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
        |   "uniqueIdentifier":"068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"
        |}""".stripMargin
    )
  }

  trait ContentNotificationScope extends NotificationScope {
    val notification = models.ContentNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
      `type` = NotificationType.Content,
      title  = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      message = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      iosUseMessage = None,
      thumbnailUrl = Some(new URI("https://media.guim.co.uk/633850064fba4941cdac17e8f6f8de97dd736029/24_0_1800_1080/500.jpg")),
      sender = "matt.wells@guardian.co.uk",
      link = Internal("world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray", Some("https://gu.com/p/4p7xt"), GITContent),
      importance = Major,
      topic = List(Topic(TagSeries, "series-a"), Topic(TagSeries, "series-b")),
      dryRun = None
    )

    val expected = Some(
      """{
        |   "t":"m",
        |   "aps":{
        |      "alert":{
        |         "body":"French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State"
        |      },
        |      "sound":"default",
        |      "category":"ITEM_CATEGORY",
        |      "mutable-content":1
        |   },
        |   "provider":"Guardian",
        |   "topics":"tag-series/series-a,tag-series/series-b",
        |   "uriType":"item",
        |   "link":"https://mobile.guardianapis.com/items/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
        |   "notificationType":"content",
        |   "uri":"https://www.theguardian.com/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
        |   "uniqueIdentifier":"068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"
        |}""".stripMargin
    )
  }

  trait MatchStatusNotificationScope extends NotificationScope {
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
      matchInfoUri = new URI("https://mobile.guardianapis.com/sport/football/match-info/3955232"),
      articleUri = Some(new URI("https://mobile.guardianapis.com/items/some-liveblog")),
      importance = Major,
      topic = List.empty,
      matchStatus = "P",
      eventId = "1000",
      debug = false,
      dryRun = None
    )

    val expected = Some(
      """{
        |   "t":"football-match-status",
        |   "aps":{
        |      "alert":{
        |         "body":"normal message",
        |         "title":"Some live event"
        |      },
        |      "sound":"default",
        |      "category":"football-match",
        |      "mutable-content":1
        |   },
        |   "provider":"Guardian",
        |   "notificationType":"football-match-status",
        |   "uniqueIdentifier":"068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7",
        |   "footballMatch":{
        |      "articleUri":"https://mobile.guardianapis.com/items/some-liveblog",
        |      "awayTeamName":"Burnley",
        |      "awayTeamScore":1,
        |      "uri":"",
        |      "currentMinute":"",
        |      "matchInfoUri":"https://mobile.guardianapis.com/sport/football/match-info/3955232",
        |      "homeTeamId":"1006",
        |      "homeTeamScore":2,
        |      "homeTeamText":"Shkodran Mustafi 59\nAlexis Sanchez 90 +7:14 Pen",
        |      "awayTeamId":"70",
        |      "awayTeamText":"Andre Gray 90 +2:41 Pen",
        |      "homeTeamName":"Arsenal",
        |      "matchStatus":"P",
        |      "competitionName":"Premier League",
        |      "mapiUrl":"https://mobile.guardianapis.com/sport/football/match-info/3955232",
        |      "venue":"Emirates Stadium",
        |      "matchId":"1000"
        |   }
        |}""".stripMargin
    )
  }

  trait NewsstandNotificationScope extends NotificationScope {
    val notification = models.NewsstandShardNotification(UUID.randomUUID(), 3)

    val expected = Some(
      """
        |{
        |   "aps":{
        |      "content-available":1
        |   }
        |}""".stripMargin
    )
  }

  trait EditionsNotificationScope extends NotificationScope {
    val notification = models.EditionsNotification(
      id = UUID.randomUUID(),
      topic = Nil,
      key = "aKey",
      name = "aName",
      date = "aDate",
      sender = "EditionsTeam"
    )

    val expected = Some(
      """
        |{
        |   "aps":{
        |      "content-available":1
        |   },
        |   "name": "aName",
        |   "date": "aDate",
        |   "key": "aKey"
        |}""".stripMargin
    )
  }
}
