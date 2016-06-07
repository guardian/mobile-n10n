package report.controllers

import java.net.URI
import java.util.UUID

import application.WithPlayApp
import models.Link.Internal
import models.Importance.Major
import models.NotificationType.BreakingNews
import models.TopicTypes.Breaking
import models._
import org.joda.time.DateTimeZone._
import org.joda.time.DateTime
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import play.api.ApplicationLoader.Context
import play.api.{BuiltInComponentsFromContext, BuiltInComponents}
import play.api.test._
import report.AppComponents
import report.services.Configuration
import tracking.InMemoryNotificationReportRepository
import scalaz.syntax.std.option._

class ReportIntegrationSpec(implicit ee: ExecutionEnv) extends PlaySpecification with Mockito {

  "Report service" should {

    "Return last 7 days notification reports if no date supplied" in new ReportTestScope {
      val result = route(FakeRequest(GET, s"/notifications/news?api-key=$apiKey")).get

      status(result) must equalTo(OK)
      contentType(result) must beSome("application/json")
      contentAsJson(result).as[List[NotificationReport]] mustEqual recentReports
    }

    "Return a list of notification reports filtered by date" in new ReportTestScope {
      val result = route(FakeRequest(GET, s"/notifications/news?from=2015-01-01T00:00:00Z&until=2015-01-02T00:00:00Z&api-key=$apiKey")).get

      status(result) must equalTo(OK)
      contentType(result) must beSome("application/json")
      contentAsJson(result).as[List[NotificationReport]] mustEqual reportsInRange
    }
  }

  trait ReportTestScope extends WithPlayApp {
    private def notificationReport(date: String, prefix: String) = {
      val id = UUID.randomUUID
      NotificationReport(
        id = id,
        sentTime = DateTime.parse(date).withZone(UTC),
        `type` = BreakingNews,
        notification = BreakingNewsNotification(
          id = id,
          sender = s"$prefix:sender",
          title = s"$prefix:title",
          message = s"$prefix:message",
          thumbnailUrl = Some(new URI(s"http://some.url/$prefix.png")),
          link = Internal(s"content/api/id/$prefix"),
          imageUrl = Some(new URI(s"http://some.url/$prefix.jpg")),
          importance = Major,
          topic = Set(Topic(Breaking, "uk"))
        ),
        reports = List(
          SenderReport("Windows", DateTime.now.withZone(UTC), PlatformStatistics(WindowsMobile, 5).some)
        )
      )
    }

    val apiKey = "test"

    val reportsInRange = List(
      notificationReport("2015-01-01T00:00:00Z", "1"),
      notificationReport("2015-01-01T04:00:00Z", "2"),
      notificationReport("2015-01-01T06:00:00Z", "3")
    )

    val recentReports = List(
      notificationReport(DateTime.now.minusDays(7).plusSeconds(10).toString, "5"),
      notificationReport(DateTime.now.minusDays(5).toString, "6"),
      notificationReport(DateTime.now.minusSeconds(1).toString, "7")
    )

    val reportRepositoryMock = {
      val notificationReports = notificationReport("2015-01-02T00:00:00Z", "4") :: reportsInRange ++ recentReports
      val repository = new InMemoryNotificationReportRepository
      notificationReports foreach repository.store
      repository
    }

    val appConfigMock = {
      val configuration = mock[Configuration]
      configuration.apiKeys returns List(apiKey)
      configuration
    }

    override def configureComponents(context: Context): BuiltInComponents = {
      new BuiltInComponentsFromContext(context) with AppComponents {
        override lazy val notificationReportRepository = reportRepositoryMock
        override lazy val appConfig = appConfigMock
      }
    }

  }
}
