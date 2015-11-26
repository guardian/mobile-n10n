package models

import java.util.UUID

import models.Link.Internal
import models.Importance.Major
import models.TopicTypes.Breaking
import org.joda.time.{DateTimeZone, DateTime}
import org.specs2.mutable.Specification
import play.api.libs.json.Json

class NotificationReportTest extends Specification {
  "NotificationReport" should {
    "Serialize sentTime to an iso date string" in {
      val json = """
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
        |      "contentApiId": "some/capi/id-with-dashes"
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
        |  "statistics": {
        |    "recipients": {}
        |  }
        |}""".stripMargin

      val report = NotificationReport.create(
        sentTime = DateTime.parse("2015-01-01T00:00:00Z").withZone(DateTimeZone.UTC),
        notification = BreakingNewsNotification(
          id = UUID.fromString("d00ceaea-8a27-11a5-9da0-a51c69a460b9"),
          sender = "sender",
          title = "title",
          message = "message",
          thumbnailUrl = Some(URL("http://some.url/my.png")),
          link = Internal("some/capi/id-with-dashes"),
          imageUrl = Some(URL("http://some.url/i.jpg")),
          importance = Major,
          topic = Set(Topic(Breaking, "uk"))
        ),
        statistics = NotificationStatistics(Map.empty)
      )

      Json.toJson(report) mustEqual Json.parse(json)
    }
  }
}
