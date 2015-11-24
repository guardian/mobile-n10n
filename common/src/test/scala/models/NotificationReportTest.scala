package models

import java.util.UUID

import org.joda.time.{DateTimeZone, DateTime}
import org.specs2.mutable.Specification
import play.api.libs.json.Json

class NotificationReportTest extends Specification {
  "NotificationReport" should {
    "Serialize sentTime to an iso date string" in {
      val json = """
        |{

        |  "id": "d00ceaea-8a27-11a5-9da0-a51c69a460b9",
        |  "sentTime": "2015-01-01T00:00:00.000Z",
        |  "sender": "sender",
        |  "type": "type",
        |  "timeToLiveInSeconds": 1,
        |  "payload": {
        |    "link": "link",
        |    "type": "type",
        |    "ticker": "ticker",
        |    "title": "title",
        |    "message": "message"
        |  },
        |  "statistics": {
        |    "recipients": {}
        |  }
        |}""".stripMargin

      val report = NotificationReport.create(
        sentTime = DateTime.parse("2015-01-01T00:00:00Z").withZone(DateTimeZone.UTC),
        notification = Notification(
          uuid = UUID.fromString("d00ceaea-8a27-11a5-9da0-a51c69a460b9"),
          sender = s"sender",
          timeToLiveInSeconds = 1,
          payload = MessagePayload(
            link = Some(s"link"),
            `type` = Some(s"type"),
            ticker = Some(s"ticker"),
            title = Some(s"title"),
            message = Some(s"message")
          )
        ),
        statistics = NotificationStatistics(Map.empty)
      )

      Json.toJson(report) mustEqual Json.parse(json)
    }
  }
}
