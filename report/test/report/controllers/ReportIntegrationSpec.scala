package report.controllers

import java.net.URI
import java.util.UUID

import models.Link.Internal
import models.Importance.Major
import models.NotificationType.BreakingNews
import models.TopicTypes.Breaking
import models._
import org.joda.time.DateTimeZone._
import org.joda.time.DateTime
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.test._
import report.services.{Configuration, NotificationReportRepositorySupport}
import tracking.InMemoryNotificationReportRepository
import scalaz.syntax.std.option._

class ReportIntegrationSpec(implicit ee: ExecutionEnv) extends PlaySpecification with Mockito {

  "Report service" should {

    "Return last 7 days notification reports if no date supplied" in new ReportTestScope {
      running(application) {
        val result = route(FakeRequest(GET, s"/notifications/news?api-key=$apiKey")).get

        status(result) must equalTo(OK)
        contentType(result) must beSome("application/json")
        charset(result) must beSome("utf-8")
        contentAsJson(result).as[List[NotificationReport]] mustEqual recentReports
      }
    }

    "Return a list of notification reports filtered by date" in new ReportTestScope {
      running(application) {
        val result = route(FakeRequest(GET, s"/notifications/news?from=2015-01-01T00:00:00Z&until=2015-01-02T00:00:00Z&api-key=$apiKey")).get

        status(result) must equalTo(OK)
        contentType(result) must beSome("application/json")
        charset(result) must beSome("utf-8")
        contentAsJson(result).as[List[NotificationReport]] mustEqual reportsInRange
      }
    }
  }

  trait ReportTestScope extends Scope {

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

    val notificationReports =
      notificationReport("2015-01-02T00:00:00Z", "4") ::
        reportsInRange ++ recentReports

    val application = {
      val reportController = {
        val repository = new InMemoryNotificationReportRepository
        notificationReports foreach repository.store

        val notificationReportRepositorySupport = mock[NotificationReportRepositorySupport]
        notificationReportRepositorySupport.notificationReportRepository returns repository

        val configuration = mock[Configuration]
        configuration.apiKey returns Some(apiKey)

        new Report(configuration, notificationReportRepositorySupport)
      }

      new GuiceApplicationBuilder()
        .overrides(bind[Report].toInstance(reportController))
        .build()
    }

  }
}
