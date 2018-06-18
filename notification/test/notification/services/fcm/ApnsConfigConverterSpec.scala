package notification.services.fcm

import java.net.URI
import java.util.UUID

import azure.apns.FootballMatchStatusProperties
import models.Importance.Major
import models.Link.Internal
import models.TopicTypes.{Breaking, TagSeries}
import models._
import notification.models.Push
import notification.models.ios.Keys
import notification.services.Configuration
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.{Configuration => PlayConfig}

class ApnsConfigConverterSpec extends Specification {
  "ApnsConfigConverter" should {
    "convert a breaking news to the firebase format" in new ApnsConfigConverterScope {
      val push = Push(
        notification = BreakingNewsNotification(
          id = UUID.fromString("4c261110-4672-4451-a5b8-3422c6839c42"),
          title = "Test notification",
          message = "The message",
          thumbnailUrl = Some(new URI("https://invalid.url/img.png")),
          sender = "UnitTests",
          link = Internal("some/capi/id", None, GITContent),
          imageUrl = Some(new URI("https://invalid.url/img.png")),
          importance = Major,
          topic = Set(Topic(`type` = Breaking, name = "uk"))
        ),
        destination = Left(Set())
      )

      val apnsNotification = convert(push)


      val expected = apnsConfigConverter.FirebaseApnsNotification(
        category = Some("ITEM_CATEGORY"),
        alert = Some(Left("The message")),
        contentAvailable = Some(true),
        sound = Some("default"),
        mutableContent = Some(true),
        customData = List(
          Keys.NotificationType -> Some("news"),
          Keys.Link -> Some("https://www.theguardian.com/some/capi/id"),
          Keys.Topics -> Some("breaking/uk"),
          Keys.Uri -> Some("https://www.theguardian.com/some/capi/id"),
          Keys.UriType -> Some("item"),
          Keys.ImageUrl -> Some("https://invalid.url/img.png")
        )
      )

      apnsNotification shouldEqual expected
    }

    "convert a content notification to the firebase format" in new ApnsConfigConverterScope {
      val push = Push(
        notification = ContentNotification(
          id = UUID.fromString("4c261110-4672-4451-a5b8-3422c6839c42"),
          title = "Test notification",
          message = "The message",
          thumbnailUrl = Some(new URI("https://invalid.url/img.png")),
          sender = "UnitTests",
          link = Internal("some/capi/id", None, GITContent),
          importance = Major,
          topic = Set(Topic(`type` = TagSeries, name = "some/tag")),
          iosUseMessage = Some(true)
        ),
        destination = Left(Set())
      )

      val apnsNotification = convert(push)


      val expected = apnsConfigConverter.FirebaseApnsNotification(
        category = Some("ITEM_CATEGORY"),
        alert = Some(Left("The message")),
        contentAvailable = Some(true),
        sound = Some("default"),
        mutableContent = None,
        customData = List(
          Keys.NotificationType -> Some("content"),
          Keys.Link -> Some("https://www.theguardian.com/some/capi/id"),
          Keys.Topics -> Some("tag-series/some/tag"),
          Keys.Uri -> Some("https://www.theguardian.com/some/capi/id"),
          Keys.UriType -> Some("item")
        )
      )

      apnsNotification shouldEqual expected
    }

    "convert a football notification to the firebase format" in new ApnsConfigConverterScope {
      val push = Push(
        notification = FootballMatchStatusNotification(
          id = UUID.fromString("4c261110-4672-4451-a5b8-3422c6839c42"),
          title = "Test notification",
          message = "The message",
          thumbnailUrl = Some(new URI("https://invalid.url/img.png")),
          sender = "UnitTests",
          importance = Major,
          topic = Set(Topic(`type` = Breaking, name = "uk")),
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
          debug = true
        ),
        destination = Left(Set())
      )

      val apnsNotification = convert(push)

      val expected = apnsConfigConverter.FirebaseApnsNotification(
        category = Some("football-match"),
        alert = Some(Right(apnsConfigConverter.FirebaseApsAlert("Test notification", "The message"))),
        contentAvailable = Some(true),
        sound = Some("default"),
        mutableContent = Some(true),
        customData = List(
          Keys.NotificationType -> Some("football-match-status"),
          "matchStatus" -> Some(FootballMatchStatusProperties(
            homeTeamName = "Team2",
            homeTeamId = "456",
            homeTeamScore = 1,
            homeTeamText = "team2 message",
            awayTeamName = "Team1",
            awayTeamId = "123",
            awayTeamScore = 0,
            awayTeamText = "team1 message",
            currentMinute = "",
            matchStatus = "1",
            matchId = "123456",
            mapiUrl = "https://some.invalid.url/detail",
            matchInfoUri = "https://some.invalid.url/detail",
            articleUri = Some("https://some.other.invalid.url/detail"),
            uri = "",
            competitionName = Some("World cup 3012"),
            venue = Some("Venue")
          ))
        )
      )

      apnsNotification shouldEqual expected
    }
  }


  trait ApnsConfigConverterScope extends Scope {
    val configuration = new Configuration(PlayConfig.empty) {
      override lazy val debug: Boolean = true
      override lazy val frontendBaseUrl: String = "https://www.theguardian.com/"
    }
    val apnsConfigConverter = new ApnsConfigConverter(configuration)

    def convert(push: Push) = {
      val Some(firebaseAndroidNotification) = apnsConfigConverter.toFirebaseApnsNotification(push)
      firebaseAndroidNotification
    }
  }
}
