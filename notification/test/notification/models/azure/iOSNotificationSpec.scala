package notification.models.azure

import java.net.URI
import java.util.UUID

import azure.apns._
import notification.services.Configuration
import models.Importance.Major
import models.Link.Internal
import models.NotificationType.{ElectionsAlert, LiveEventAlert, FootballMatchStatus}
import models._
import models.TopicTypes.{Breaking, LiveNotification, TagSeries}
import models.elections.{CandidateResults, ElectionResults}
import notification.models.Push
import notification.services.azure.APNSPushConverter
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class iOSNotificationSpec extends Specification with Mockito {

  "A breaking news" should {
    "serialize / deserialize to json" in new BreakingNewsScope {
      converter.toRawPush(push).get.body shouldEqual expected
    }

    "use imageUrl if thumbnail is not available" in new BreakingNewsScopeNoThumbnail {
      converter.toRawPush(push).get.body shouldEqual expected
    }

    "serialize / deserialize to json without mutable flag if there is no image" in new BreakingNewsScopeNoImage {
      converter.toRawPush(push).get.body shouldEqual expected
    }
  }

  "A content notification" should {
    "serialize / deserialize to json" in new ContentNotificationScope {
      converter.toRawPush(push).map(_.body) should beSome(expected)
    }
  }

  "An election notification" should {
    "serialize / deserialize to json" in new ElectionNotificationScope {
      converter.toRawPush(push).map(_.body) should beSome(expected)
    }
  }

  "A live notification" should {
    "serialize / deserialize to json" in new LiveEventNotificationScope {
      converter.toRawPush(push).map(_.body) should beSome(expected)
    }
  }

  "A match status notification" should {
    "serialize / deserialize to json" in new MatchStatusNotificationScope {
      converter.toRawPush(push).map(_.body) should beSome(expected)
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
      title  = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      message = "French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State",
      thumbnailUrl = Some(new URI("https://media.guim.co.uk/633850064fba4941cdac17e8f6f8de97dd736029/24_0_1800_1080/500.jpg")),
      sender = "matt.wells@guardian.co.uk",
      link = Internal("world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray", Some("https://gu.com/p/4p7xt"), GITContent),
      imageUrl = Some(new URI("https://media.guim.co.uk/633850064fba4941cdac17e8f6f8de97dd736029/24_0_1800_1080/500-image-url.jpg")),
      importance = Major,
      topic = Set(Topic(Breaking, "uk"), Topic(Breaking, "us"), Topic(Breaking, "au"), Topic(Breaking, "international"))
    )

    val push = Push(notification, Left(Set(Topic(Breaking, "uk"), Topic(Breaking, "us"), Topic(Breaking, "au"), Topic(Breaking, "international"))))

    val expected = Body(
      aps = APS(
        alert = Some(Right("French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State")),
        category = Some("ITEM_CATEGORY"),
        `content-available` = Some(1),
        sound = Some("default"),
        `mutable-content` = Some(1)
      ),
      customProperties = LegacyProperties(Map(
        "t" -> "m",
        "notificationType" -> "news",
        "link" -> "x-gu:///p/4p7xt",
        "topics" -> "breaking/uk,breaking/us,breaking/au,breaking/international",
        "uri" -> "https://www.theguardian.com/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
        "imageUrl" -> "https://media.guim.co.uk/633850064fba4941cdac17e8f6f8de97dd736029/24_0_1800_1080/500.jpg",
        "uriType" -> "item"
      ))
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
      topic = Set(Topic(Breaking, "uk"), Topic(Breaking, "us"), Topic(Breaking, "au"), Topic(Breaking, "international"))
    )

    val push = Push(notification, Left(Set(Topic(Breaking, "uk"), Topic(Breaking, "us"), Topic(Breaking, "au"), Topic(Breaking, "international"))))

    val expected = Body(
      aps = APS(
        alert = Some(Right("French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State")),
        category = Some("ITEM_CATEGORY"),
        `content-available` = Some(1),
        sound = Some("default"),
        `mutable-content` = Some(1)
      ),
      customProperties = LegacyProperties(Map(
        "t" -> "m",
        "notificationType" -> "news",
        "link" -> "x-gu:///p/4p7xt",
        "topics" -> "breaking/uk,breaking/us,breaking/au,breaking/international",
        "uri" -> "https://www.theguardian.com/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
        "imageUrl" -> "https://media.guim.co.uk/633850064fba4941cdac17e8f6f8de97dd736029/24_0_1800_1080/500-image-url.jpg",
        "uriType" -> "item"
      ))
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
      topic = Set(Topic(Breaking, "uk"), Topic(Breaking, "us"), Topic(Breaking, "au"), Topic(Breaking, "international"))
    )

    val push = Push(notification, Left(Set(Topic(Breaking, "uk"), Topic(Breaking, "us"), Topic(Breaking, "au"), Topic(Breaking, "international"))))

    val expected = Body(
      aps = APS(
        alert = Some(Right("French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State")),
        category = Some("ITEM_CATEGORY"),
        `content-available` = Some(1),
        sound = Some("default")
      ),
      customProperties = LegacyProperties(Map(
        "t" -> "m",
        "notificationType" -> "news",
        "link" -> "x-gu:///p/4p7xt",
        "topics" -> "breaking/uk,breaking/us,breaking/au,breaking/international",
        "uri" -> "https://www.theguardian.com/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
        "uriType" -> "item"
      ))
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
      topic = Set(Topic(TagSeries, "series-a"), Topic(TagSeries, "series-b"))
    )

    val push = Push(notification, Left(Set(Topic(TagSeries, "series-a"), Topic(TagSeries, "series-b"))))

    val expected = Body(
      aps = APS(
        alert = Some(Right("French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State")),
        category = Some("ITEM_CATEGORY"),
        `content-available` = Some(1),
        sound = Some("default")
      ),
      customProperties = LegacyProperties(Map(
        "t" -> "m",
        "notificationType" -> "content",
        "link" -> "x-gu:///p/4p7xt",
        "topics" -> "tag-series/series-a,tag-series/series-b",
        "uri" -> "https://www.theguardian.com/world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray",
        "uriType" -> "item"
      ))
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
        alert = Some(Right("Leicester 2-1 Watford\nDeeney 75min (o.g.)")),
        category = None,
        `content-available` = Some(1),
        sound = Some("default")
      ),
      customProperties = LegacyProperties(Map(
        "t" -> "g",
        "notificationType" -> "goal",
        "uri" -> "x-gu:///match-info/3833380",
        "uriType" -> "football-match"
      ))
    )
  }

  trait ElectionNotificationScope extends NotificationScope {
    val notification = models.ElectionNotification(
      id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
      message = "\u2022 Electoral votes: Clinton 220, Trump 133\n\u2022 270 electoral votes to win\n• 35 states called, 5 swing states (OH, PA, NV, CO, FL)\n• Popular vote: Clinton 52%, Trump 43% with 42% precincts reporting",
      shortMessage = Some("this is the short message"),
      expandedMessage = Some("this is the expanded message"),
      sender = "some-sender",
      title = "Live election results",
      importance = Major,
      link = Internal("us", Some("https://gu.com/p/4p7xt"), GITContent),
      resultsLink = Internal("us", Some("https://gu.com/p/2zzz"), GITContent),
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

    val expected = Body(
      aps = APS(
        alert = None,
        category = None,
        `content-available` = Some(1),
        sound = None
      ),
      customProperties = StandardProperties(
        t = "us-election",
        notificationType = ElectionsAlert,
        election = Some(ElectionProperties(
          title = "Live election results",
          body = "\u2022 Electoral votes: Clinton 220, Trump 133\n\u2022 270 electoral votes to win\n• 35 states called, 5 swing states (OH, PA, NV, CO, FL)\n• Popular vote: Clinton 52%, Trump 43% with 42% precincts reporting",
          richviewbody = "this is the expanded message",
          sound = 1,
          dem = 220,
          rep = 133,
          link = "x-gu:///p/4p7xt",
          results = "x-gu:///p/2zzz"
        ))
      )
    )
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

    val expected = Body(
      aps = APS(
        alert = None,
        category = None,
        `content-available` = Some(1),
        sound = None
      ),
      customProperties = StandardProperties(
        t = "live",
        notificationType = LiveEventAlert,
        liveEvent = Some(LiveEventProperties(
          title = "Some live event",
          body = "normal message",
          richviewbody = "this is the expanded message",
          sound = 1,
          link1 = "x-gu:///p/4p7xt",
          link2 = "x-gu:///p/5982v",
          imageURL = Some("http://gu.com/some-image.png"),
          topics = "live-notification/super-bowl-li"
        ))
      )
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
      topic = Set.empty,
      matchStatus = "P",
      eventId = "1000",
      debug = false
    )

    val push = Push(notification, Left(notification.topic))

    val expected = Body(
      aps = APS(
        alert = Some(Left(Alert(title = Some("Some live event"), body = Some("normal message")))),
        category = Some("football-match"),
        `mutable-content` = Some(1),
        sound = Some("default")
      ),
      customProperties = StandardProperties(
        t = "football-match-status",
        notificationType = FootballMatchStatus,
        footballMatch = Some(FootballMatchStatusProperties(
          homeTeamName = "Arsenal",
          homeTeamId = "1006",
          homeTeamScore = 2,
          homeTeamText = "Shkodran Mustafi 59\nAlexis Sanchez 90 +7:14 Pen",
          awayTeamName = "Burnley",
          awayTeamId = "70",
          awayTeamScore = 1,
          awayTeamText = "Andre Gray 90 +2:41 Pen",
          currentMinute = "",
          matchStatus = "P",
          matchId = "1000",
          mapiUrl = "https://mobile.guardianapis.com/sport/football/match-info/3955232",
          matchInfoUri = "https://mobile.guardianapis.com/sport/football/match-info/3955232",
          articleUri = Some("https://mobile.guardianapis.com/items/some-liveblog"),
          uri = "",
          competitionName = Some("Premier League"),
          venue = Some("Emirates Stadium")
        ))
      )
    )
  }

}
