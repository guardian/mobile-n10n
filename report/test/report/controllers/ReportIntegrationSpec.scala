package report.controllers

import java.net.URI
import java.util.UUID

import application.WithPlayApp
import azure.{NotificationDetails, NotificationStates}
import cats.data.NonEmptyList
import cats.effect.IO
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
import play.api.{BuiltInComponents, BuiltInComponentsFromContext}
import play.api.test._
import report.ReportApplicationComponents
import report.services.{Configuration, NotificationReportEnricher}
import tracking.InMemoryNotificationReportRepository
import cats.implicits._
import com.softwaremill.macwire._
import db.{RegistrationRepository, RegistrationService}

import scala.concurrent.Future

class ReportIntegrationSpec(implicit ee: ExecutionEnv) extends PlaySpecification with Mockito {

  "Report service" should {

    "Return last 7 days notification reports if no date supplied" in new ReportTestScope {
      val result = route(app, FakeRequest(GET, s"/notifications?type=news&api-key=$apiKey")).get

      status(result) must equalTo(OK)
      contentType(result) must beSome("application/json")
      contentAsJson(result).as[List[DynamoNotificationReport]] mustEqual recentReports
    }

    "Return a list of notification reports filtered by date" in new ReportTestScope {
      val result = route(app, FakeRequest(GET, s"/notifications?type=news&from=2015-01-01T00:00:00Z&until=2015-01-02T00:00:00Z&api-key=$apiKey")).get

      status(result) must equalTo(OK)
      contentType(result) must beSome("application/json")
      contentAsJson(result).as[List[DynamoNotificationReport]] mustEqual reportsInRange
    }

    "Return an individual notification report complete with platform specific data" in new ReportTestScope {
      val id = reportsInRange.head.id
      val result = route(app, FakeRequest(GET, s"/notifications/$id?api-key=$apiKey")).get

      status(result) must equalTo(OK)
      contentType(result) must beSome("application/json")
      contentAsJson(result).as[ExtendedNotificationReport].reports.head.debug must beSome
    }
  }

  trait ReportTestScope extends WithPlayApp {
    private def notificationReport(date: String, prefix: String) = {
      val id = UUID.randomUUID
      DynamoNotificationReport(
        id = id,
        sentTime = DateTime.parse(date).withZone(UTC),
        `type` = BreakingNews,
        notification = BreakingNewsNotification(
          id = id,
          sender = s"$prefix:sender",
          title = s"$prefix:title",
          message = s"$prefix:message",
          thumbnailUrl = Some(new URI(s"http://some.url/$prefix.png")),
          link = Internal(s"content/api/id/$prefix", None, GITContent),
          imageUrl = Some(new URI(s"http://some.url/$prefix.jpg")),
          importance = Major,
          topic = List(Topic(Breaking, "uk"))
        ),
        reports = List(
          SenderReport("Firebase", DateTime.now.withZone(UTC), Some(s"hub-$id"), Some(PlatformStatistics(Android, 5)))
        ),
        version = Some(UUID.randomUUID()),
        events = None
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
      configuration.electionRestrictedApiKeys returns List.empty
      configuration.reportsOnlyApiKeys returns List.empty
      configuration
    }

    val notificationReportEnricherMock = {
      val notificationReportEnricher = mock[NotificationReportEnricher]

      val report = reportsInRange.head

      val enrichedReport = ExtendedNotificationReport.fromNotificationReport(report)

      val details = NotificationDetails(
        state = NotificationStates.Completed,
        enqueueTime = report.sentTime.plusSeconds(5),
        startTime = Some(report.sentTime.plusSeconds(10)),
        endTime = Some(report.sentTime.plusSeconds(20)),
        notificationBody = "test",
        targetPlatforms = List.empty,
        wnsOutcomeCounts = None,
        apnsOutcomeCounts = None,
        gcmOutcomeCounts = None,
        tags = "test,tags,list",
        pnsErrorDetailsUri = None
      )

      val enrichedReportWithDebug = enrichedReport.copy(
        reports = enrichedReport.reports.map(report => report.copy(debug = Some(details)))
      )

      notificationReportEnricher.enrich(reportsInRange.head) returns Future.successful(enrichedReportWithDebug)
      notificationReportEnricher
    }

    override def configureComponents(context: Context): BuiltInComponents = {
      new ReportApplicationComponents(context) {
        override lazy val reportEnricher = notificationReportEnricherMock
        override lazy val notificationReportRepository = reportRepositoryMock
        override lazy val appConfig = appConfigMock
        override lazy val registrationDbService: RegistrationService[IO, fs2.Stream] = new RegistrationService(
          new RegistrationRepository[IO, fs2.Stream] {
            override def findByTopic(topic: db.Topic): fs2.Stream[IO, db.Registration] = ???
            override def findByToken(token: String): fs2.Stream[IO, db.Registration] = ???
            override def save(sub: db.Registration): IO[Port] = ???
            override def remove(sub: db.Registration): IO[Port] = ???
            override def removeByToken(token: String): IO[Port] = ???
            override def countPerPlatformForTopics(topics: NonEmptyList[db.Topic]): IO[PlatformCount] = ???
          }
        )
      }
    }

  }
}
