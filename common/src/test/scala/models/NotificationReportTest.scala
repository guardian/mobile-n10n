package models

import java.net.URI
import java.util.UUID

import models.Link.Internal
import models.Importance.Major
import models.TopicTypes.Breaking
import org.joda.time.{DateTime, DateTimeZone}
import org.specs2.mutable.Specification
import play.api.libs.json.Json
import cats.implicits._
import org.specs2.specification.Scope

class NotificationReportTest extends Specification {
  "NotificationReport" should {
    "Serialize sentTime to an iso date string" in {
      val json =
        """
          |{
          |  "id": "d00ceaea-8a27-11a5-9da0-a51c69a460b9",
          |  "type": "news",
          |  "notification": {
          |    "id": "d00ceaea-8a27-11a5-9da0-a51c69a460b9",
          |    "type": "news",
          |    "title": "title",
          |    "message": "message",
          |    "thumbnailUrl": "http://some.url/my.png",
          |    "sender": "sender",
          |    "link": {
          |      "contentApiId": "some/capi/id-with-dashes",
          |      "git":{"mobileAggregatorPrefix":"item-trimmed"}
          |    },
          |    "imageUrl": "http://some.url/i.jpg",
          |    "importance": "Major",
          |    "topic": [
          |      {
          |        "type": "breaking",
          |        "name": "uk"
          |      }
          |    ]
          |  },
          |  "sentTime": "2015-01-01T00:00:00.000Z",
          |  "reports": [
          |    {
          |     "senderName": "Firebase",
          |     "sentTime": "2015-01-01T00:00:00.000Z",
          |     "platformStatistics": {
          |       "platform": "android",
          |       "recipientsCount": 3
          |     }
          |    }
          |
          | ]
          |}""".stripMargin

      val report = {
        val sentTime = DateTime.parse("2015-01-01T00:00:00Z").withZone(DateTimeZone.UTC)
        NotificationReport.create(
          notification = BreakingNewsNotification(
            id = UUID.fromString("d00ceaea-8a27-11a5-9da0-a51c69a460b9"),
            sender = "sender",
            title = Some("title"),
            message = Some("message"),
            thumbnailUrl = Some(new URI("http://some.url/my.png")),
            link = Internal("some/capi/id-with-dashes", None, GITContent),
            imageUrl = Some(new URI("http://some.url/i.jpg")),
            importance = Major,
            topic = List(Topic(Breaking, "uk")),
            dryRun = None
          ),
          reports = List(
            SenderReport("Firebase", sentTime, None, Some(PlatformStatistics(Android, recipientsCount = 3)))
          )
        )
      }

      Json.toJson(report) mustEqual Json.parse(json)
    }

    "have no TTL for breaking news" in new NotificationReportScope {
      val report = NotificationReport.create(
        notification = breakingNews,
        reports = List(
          SenderReport("Firebase", DateTime.now(), None, Some(PlatformStatistics(Android, recipientsCount = 3)))
        )
      )

      report.ttl should beNone
    }

    "have a TTL for dry run breaking news" in new NotificationReportScope {
      val report = NotificationReport.create(
        notification = breakingNews.copy(dryRun = Some(true)),
        reports = List(
          SenderReport("Firebase", DateTime.now(), None, Some(PlatformStatistics(Android, recipientsCount = 3)))
        )
      )

      report.ttl should beSome
    }
  }

  trait NotificationReportScope extends Scope {
    val breakingNews = BreakingNewsNotification(
      id = UUID.fromString("d00ceaea-8a27-11a5-9da0-a51c69a460b9"),
      sender = "sender",
      title = Some("title"),
      message = Some("message"),
      thumbnailUrl = Some(new URI("http://some.url/my.png")),
      link = Internal("some/capi/id-with-dashes", None, GITContent),
      imageUrl = Some(new URI("http://some.url/i.jpg")),
      importance = Major,
      topic = List(Topic(Breaking, "uk")),
      dryRun = None
    )
  }
}
