package report.services

import java.util.UUID

import azure.{NotificationDetails, NotificationHubClient, NotificationStates}
import models.Importance.Major
import models.NotificationType.BreakingNews
import models._
import org.joda.time.DateTime
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import cats.implicits._

import scala.concurrent.Future

class NotificationReportEnricherSpec(implicit ev: ExecutionEnv) extends Specification with Mockito {
  "A NotificationReportEnricher" should {
    "Add notification telemtry from Azure to a notification report" in new NotificationReportContext {
      enricher.enrich(report) must beEqualTo(expected).await
    }
  }

  trait NotificationReportContext extends Scope {
    val hubClient = mock[NotificationHubClient]
    val enricher = new NotificationReportEnricher(hubClient)

    val id = UUID.randomUUID()
    val version = UUID.randomUUID()
    val sentTime = DateTime.now

    val notification = BreakingNewsNotification(
      id = id,
      title = "test",
      message = "test",
      thumbnailUrl = None,
      sender = "unit-test",
      link = Link.External("http://www.theguardian.com"),
      imageUrl = None,
      importance = Major,
      topic = List(Topic(TopicTypes.Breaking, "uk"))
    )

    def createSenderReport(id: String) = SenderReport(
      senderName = Provider.Azure.value,
      sentTime = sentTime,
      sendersId = Some(s"https://guardian-windows-10-live-ns.servicebus.windows.net/guardian-notification-prod/messages/$id?api-version=2015-01"),
      platformStatistics = None
    )

    def createNotificationDetails(body: String) = NotificationDetails(
      state = NotificationStates.Completed,
      enqueueTime = sentTime.plusSeconds(5),
      startTime = Some(sentTime.plusSeconds(10)),
      endTime = Some(sentTime.plusSeconds(20)),
      notificationBody = body,
      targetPlatforms = List.empty,
      wnsOutcomeCounts = None,
      apnsOutcomeCounts = None,
      gcmOutcomeCounts = None,
      tags = "test,tags,list",
      pnsErrorDetailsUri = None
    )

    val senderReportsWithDetails = for {
      id <- List("id-1", "id-2", "id-3")
    } yield {
      (id, createSenderReport(id), createNotificationDetails(s"message-for-$id"))
    }

    senderReportsWithDetails.foreach { case (sendersId, _, details) =>
      hubClient.notificationDetails(sendersId) returns Future.successful(Right(details))
    }

    val report = DynamoNotificationReport(
      id = id,
      `type` =  BreakingNews,
      notification = notification,
      sentTime = sentTime,
      reports = senderReportsWithDetails.map(_._2),
      version = Some(version),
      None
    )

    val expected = {
      val enriched = ExtendedNotificationReport.fromNotificationReport(report)
      enriched.copy(reports = senderReportsWithDetails.map { case (_, senderReport, details) =>
        ExtendedSenderReport.fromSenderReport(senderReport).copy(debug = Some(details))
      })
    }
  }
}
