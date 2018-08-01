package notification.services.fcm

import java.net.URI
import java.util.UUID

import models.Importance.Major
import models.Link.Internal
import models.TopicTypes.Breaking
import models._
import notification.models.Push
import notification.models.android.Keys
import notification.services.Configuration
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.{Configuration => PlayConfig}

class AndroidConfigConverterSpec extends Specification {
  "AndroidConfigConverter" should {
    "convert a breaking news to the firebase format" in new AndroidConfigConverterScope {
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
          topic = List(Topic(`type` = Breaking, name = "uk"))
        ),
        destination = Set()
      )

      val data = convert(push)

      data shouldEqual Map(
        Keys.UniqueIdentifier -> "4c261110-4672-4451-a5b8-3422c6839c42",
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
      )
    }

    "convert a content notification to the firebase format" in new AndroidConfigConverterScope {
      val push = Push(
        notification = ContentNotification(
          id = UUID.fromString("4c261110-4672-4451-a5b8-3422c6839c42"),
          title = "Test notification",
          message = "The message",
          thumbnailUrl = Some(new URI("https://invalid.url/img.png")),
          sender = "UnitTests",
          link = Internal("some/capi/id", None, GITContent),
          importance = Major,
          topic = List(Topic(`type` = Breaking, name = "uk")),
          iosUseMessage = Some(true)
        ),
        destination = Set()
      )

      val data = convert(push)

      data shouldEqual Map(
        Keys.UniqueIdentifier -> "4c261110-4672-4451-a5b8-3422c6839c42",
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
      )
    }

    "convert a football notification to the firebase format" in new AndroidConfigConverterScope {
      val push = Push(
        notification = FootballMatchStatusNotification(
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
          debug = true
        ),
        destination = Set()
      )

      val data = convert(push)

      data shouldEqual Map(
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
      )
    }
  }


  trait AndroidConfigConverterScope extends Scope {
    val configuration = new Configuration(PlayConfig.empty) {
      override lazy val debug: Boolean = true
      override lazy val frontendBaseUrl: String = "https://www.theguardian.com"
    }
    val androidConfigConverter = new AndroidConfigConverter(configuration)

    def convert(push: Push) = {
      val Some(firebaseAndroidNotification) = androidConfigConverter.toFirebaseAndroidNotification(push)
      firebaseAndroidNotification.data
    }
  }
}
