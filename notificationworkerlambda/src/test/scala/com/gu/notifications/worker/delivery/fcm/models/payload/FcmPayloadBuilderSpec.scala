package com.gu.notifications.worker.delivery.fcm.models.payload

import java.net.URI
import java.util.UUID

import com.gu.notifications.worker.delivery.fcm.models.payload.FcmPayloadBuilder.FirebaseAndroidNotification
import models.Importance.{Major, Minor}
import models.Link.Internal
import models.TopicTypes.Breaking
import models.{GITContent, Notification, Topic}
import org.specs2.matcher.Matchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import com.gu.notifications.worker.delivery.utils.TimeToLive

class FcmPayloadBuilderSpec extends Specification with Matchers {

  "FcmPayload" should {
    "generate correct data for Breaking News notification" in new BreakingNewsScope {
      check()
    }
    "generate correct data for Content notification" in new ContentNotificationScope {
      check()
    }
    "generate correct data for Match Status notification" in new MatchStatusNotificationScope {
      check()
    }
    "generate correct data for Editions notification" in new EditionsScope {
      check()
    }
  }

  trait NotificationScope extends Scope {
    def notification: Notification
    def expected: Option[FirebaseAndroidNotification]

    def check() = FirebaseAndroidNotification(notification, debug = true) shouldEqual(expected)
  }

  trait BreakingNewsScope extends NotificationScope {
    val notification = models.BreakingNewsNotification(
      id = UUID.fromString("4c261110-4672-4451-a5b8-3422c6839c42"),
      title = "Test notification",
      message = "The message",
      thumbnailUrl = Some(new URI("https://invalid.url/img.png")),
      sender = "UnitTests",
      link = Internal("some/capi/id", None, GITContent),
      imageUrl = Some(new URI("https://invalid.url/img.png")),
      importance = Major,
      topic = List(Topic(`type` = Breaking, name = "uk")),
      dryRun = None
    )

    val expected = Some(
      FirebaseAndroidNotification(
        notificationId = UUID.fromString("4c261110-4672-4451-a5b8-3422c6839c42"),
        data = Map(
          Keys.NotificationType -> "news",
          Keys.Type -> "custom",
          Keys.Title -> "Test notification",
          Keys.Ticker -> "The message",
          Keys.Message -> "The message",
          Keys.Debug -> "true",
          Keys.Editions -> "uk",
          Keys.Link -> "x-gu://www.guardian.co.uk/some/capi/id",
          Keys.UriType -> "item",
          Keys.Uri -> "x-gu:///items/some/capi/id",
          Keys.Edition -> "uk",
          Keys.ImageUrl -> "https://invalid.url/img.png",
          Keys.ThumbnailUrl -> "https://invalid.url/img.png"
        ),
        ttl = TimeToLive.BreakingNewsTtl      )
    )
  }

  trait EditionsScope extends NotificationScope {
     val notification = models.EditionsShardNotification(
       id = UUID.fromString("4c261110-4672-4451-a5b8-3422c6839c42"),
       shard = 123,
     )

    val expected = Some(
      FirebaseAndroidNotification(
        notificationId = UUID.fromString("4c261110-4672-4451-a5b8-3422c6839c42"),
        data = Map(
          Keys.NotificationType -> "editions-shard",
          Keys.Type -> "custom",
          Keys.Message -> "guardian-editions",
          Keys.Topics -> "editions-shard//editions-shard-123",
          Keys.Importance -> "Minor"
        ),
        ttl = TimeToLive.DefaulTtl)
    )
  }


  trait ContentNotificationScope extends NotificationScope {
    val notification = models.ContentNotification(
      id = UUID.fromString("4c261110-4672-4451-a5b8-3422c6839c42"),
      title = "Test notification",
      message = "The message",
      thumbnailUrl = Some(new URI("https://invalid.url/img.png")),
      sender = "UnitTests",
      link = Internal("some/capi/id", None, GITContent),
      importance = Major,
      topic = List(Topic(`type` = Breaking, name = "uk")),
      iosUseMessage = Some(true),
      dryRun = None
    )

    val expected = Some(
      FirebaseAndroidNotification(
        notificationId = UUID.fromString("4c261110-4672-4451-a5b8-3422c6839c42"),
        data = Map(
          Keys.Type -> "custom",
          Keys.Title -> "Test notification",
          Keys.Ticker -> "The message",
          Keys.Message -> "The message",
          Keys.Debug -> "true",
          Keys.Link -> "x-gu://www.guardian.co.uk/some/capi/id",
          Keys.Topics -> "breaking//uk",
          Keys.UriType -> "item",
          Keys.Uri -> "x-gu:///items/some/capi/id",
          Keys.ThumbnailUrl -> "https://invalid.url/img.png"
        ),
        ttl = TimeToLive.DefaulTtl
      )
    )
  }

  trait MatchStatusNotificationScope extends NotificationScope {
    val notification = models.FootballMatchStatusNotification(
      id = UUID.fromString("4c261110-4672-4451-a5b8-3422c6839c42"),
      title = "Test notification",
      message = "The message",
      thumbnailUrl = Some(new URI("https://invalid.url/img.png")),
      sender = "UnitTests",
      importance = Major,
      topic = List(Topic(`type` = Breaking, name = "uk")),
      awayTeamName = "Team1",
      awayTeamScore = 0,
      awayTeamMessage = "team1 message",
      awayTeamId = "123",
      homeTeamName = "Team2",
      homeTeamScore = 1,
      homeTeamMessage = "team2 message",
      homeTeamId = "456",
      competitionName = Some("World cup 3012"),
      venue = Some("Venue"),
      matchId = "123456",
      matchInfoUri = new URI("https://some.invalid.url/detail"),
      articleUri = Some(new URI("https://some.other.invalid.url/detail")),
      matchStatus = "1",
      eventId = "2",
      debug = true,
      dryRun = None
    )

    val expected = Some(
      FirebaseAndroidNotification(
        notificationId = UUID.fromString("4c261110-4672-4451-a5b8-3422c6839c42"),
        data = Map(
          "type" -> "footballMatchAlert",
          "homeTeamName" -> "Team2",
          "homeTeamId" -> "456",
          "homeTeamScore" -> "1",
          "homeTeamText" -> "team2 message",
          "awayTeamName" -> "Team1",
          "awayTeamId" -> "123",
          "awayTeamScore" -> "0",
          "awayTeamText" -> "team1 message",
          "currentMinute" -> "",
          "importance" -> "Major",
          "matchStatus" -> "1",
          "matchId" -> "123456",
          "matchInfoUri" -> "https://some.invalid.url/detail",
          "articleUri" -> "https://some.other.invalid.url/detail",
          "competitionName" -> "World cup 3012",
          "venue" -> "Venue"
        ),
        ttl = TimeToLive.FootballMatchStatusTtl
      )
    )

  }
}
