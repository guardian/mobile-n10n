package report.controllers

import java.net.URI
import java.util.UUID

import application.WithPlayApp
import cats.data.NonEmptyList
import cats.effect.IO
import models.Link.Internal
import models.Importance.Major
import models.NotificationType.{BreakingNews, Content}
import models.TopicTypes.Breaking
import models._
import org.joda.time.DateTimeZone._
import org.joda.time.DateTime
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponents
import play.api.test._
import report.ReportApplicationComponents
import report.services.Configuration
import tracking.InMemoryNotificationReportRepository
import db.{RegistrationRepository, RegistrationService}

import scala.concurrent.Future

class ReportIntegrationSpec(implicit ee: ExecutionEnv) extends PlaySpecification with Mockito {

  "Report service" should {

    "Return last 7 days notification reports if no date supplied" in new ReportTestScope {
      val result = route(app, FakeRequest(GET, s"/notifications?type=news").withHeaders("Authorization" -> s"Bearer $apiKey")).get

      status(result) must equalTo(OK)
      contentType(result) must beSome("application/json")

      val dynamoNotificationReports: List[DynamoNotificationReport] = contentAsJson(result).as[List[DynamoNotificationReport]]
      dynamoNotificationReports.flatMap(_.ttl) must beEmpty
      dynamoNotificationReports mustEqual recentReports
    }

    "Return a list of notification reports filtered by date" in new ReportTestScope {
      val result = route(app, FakeRequest(GET, s"/notifications?type=news&from=2015-01-01T00:00:00Z&until=2015-01-02T00:00:00Z").withHeaders("Authorization" -> s"Bearer $apiKey")).get

      status(result) must equalTo(OK)
      contentType(result) must beSome("application/json")
      contentAsJson(result).as[List[DynamoNotificationReport]] mustEqual reportsInRange
    }


    "Return content notification reports" in new ReportTestScope {
      val result = route(app, FakeRequest(GET, s"/notifications?type=content").withHeaders("Authorization" -> s"Bearer $apiKey")).get

      status(result) must equalTo(OK)
      contentType(result) must beSome("application/json")
      val dynamoNotificationReports: List[DynamoNotificationReport] = contentAsJson(result).as[List[DynamoNotificationReport]]
      dynamoNotificationReports.flatMap(_.ttl) must not be empty
      dynamoNotificationReports mustEqual recentContentReports
    }

    "Return an individual notification report complete with platform specific data" in new ReportTestScope {
      val id = reportsInRange.head.id
      val result = route(app, FakeRequest(GET, s"/notifications/$id").withHeaders("Authorization" -> s"Bearer $apiKey")).get

      status(result) must equalTo(OK)
      contentType(result) must beSome("application/json")
    }
  }

  trait ReportTestScope extends WithPlayApp {
    private def notificationReport(date: String, prefix: String) = {
      val id = UUID.randomUUID
      DynamoNotificationReport.create(
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
    private def contentReport(date: String, prefix: String) = {
      val id = UUID.randomUUID
      DynamoNotificationReport.create(
        id = id,
        sentTime = DateTime.parse(date).withZone(UTC),
        `type` = Content,
        notification = ContentNotification(
          id = id,
          sender = s"$prefix:sender",
          title = s"$prefix:title",
          message = s"$prefix:message",
          thumbnailUrl = Some(new URI(s"http://some.url/$prefix.png")),
          link = Internal(s"content/api/id/$prefix", None, GITContent),
          importance = Major,
          topic = List(Topic(Breaking, "uk")),
          iosUseMessage = None
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
      notificationReport(DateTime.now.minusDays(7).plusMinutes(3).toString, "5"),
      notificationReport(DateTime.now.minusDays(5).toString, "6"),
      notificationReport(DateTime.now.minusSeconds(1).toString, "7")
    )

    val recentContentReports = List(
      contentReport(DateTime.now.minusDays(5).toString, "8")
    )
    val reportRepositoryMock = {
      val notificationReports = notificationReport("2015-01-02T00:00:00Z", "4") :: reportsInRange ++ recentReports ++ recentContentReports
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

    override def configureComponents(context: Context): BuiltInComponents = {
      new ReportApplicationComponents(context) {
        override lazy val notificationReportRepository = reportRepositoryMock
        override lazy val appConfig = appConfigMock
        override lazy val registrationDbService: RegistrationService[IO, fs2.Stream] = new RegistrationService(
          new RegistrationRepository[IO, fs2.Stream] {
            override def findTokens(topics: NonEmptyList[String], platform: Option[String], shardRange: Option[Range]): fs2.Stream[IO, String] = ???
            override def findByToken(token: String): fs2.Stream[IO, db.Registration] = ???
            override def save(sub: db.Registration): IO[Port] = ???
            override def remove(sub: db.Registration): IO[Port] = ???
            override def removeByToken(token: String): IO[Port] = ???
            override def countPerPlatformForTopics(topics: NonEmptyList[db.Topic]): IO[PlatformCount] = ???

            override def topicCounts: fs2.Stream[IO, TopicCount] = ???
          }
        )
      }
    }

  }
}
