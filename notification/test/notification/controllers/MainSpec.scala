package notification.controllers

import java.util.UUID

import models.Importance.Major
import models.Link.Internal
import models.TopicTypes.Breaking
import models._
import notification.models.Push
import notification.services.{NotificationReportRepositorySupport, NotificationSender, NotificationSenderSupport, Configuration}
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.test.{FakeRequest, PlaySpecification}
import providers.Error
import tracking.InMemoryNotificationReportRepository
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scalaz.\/
import scalaz.syntax.either._

class MainSpec extends PlaySpecification with Mockito {

  "The Main controller" should {
    "send a notification to multiple topics" in new MainScope with NotificationScope {
      val topics = Set(Topic(Breaking, "uk"), Topic(Breaking, "us"))
      val request = authenticatedRequest.withBody(notification(topics))
      val response = main.pushTopics()(request)

      status(response) must equalTo(CREATED)
      pushSent must beSome.which(_.destination must beEqualTo(Left(topics)))
    }
    "refuse a notification without a topic" in new MainScope with NotificationScope {
      val request = authenticatedRequest.withBody(notification(Set()))
      status(main.pushTopics()(request)) must equalTo(BAD_REQUEST)
    }
    "refuse a notification with too many topics" in new MainScope with NotificationScope {
      val topics = (1 to 21).map(i => Topic(Breaking, s"$i"))
      val request = authenticatedRequest.withBody(notification(Set()))
      status(main.pushTopics()(request)) must equalTo(BAD_REQUEST)
    }
  }

  trait NotificationScope extends Scope {
    def notification(topics: Set[Topic]): Notification = BreakingNewsNotification(
      id = UUID.fromString("30aac5f5-34bb-4a88-8b69-97f995a4907b"),
      title = "The Guardian",
      message = "Mali hotel attack: UN counts 27 bodies as hostage situation ends",
      thumbnailUrl = Some(URL("http://media.guim.co.uk/09951387fda453719fe1fee3e5dcea4efa05e4fa/0_181_3596_2160/140.jpg")),
      sender = "test",
      link = Internal("world/live/2015/nov/20/mali-hotel-attack-gunmen-take-hostages-in-bamako-live-updates"),
      imageUrl = Some(URL("https://mobile.guardianapis.com/img/media/a5fb401022d09b2f624a0cc0484c563fd1b6ad93/" +
        "0_308_4607_2764/master/4607.jpg/6ad3110822bdb2d1d7e8034bcef5dccf?width=800&height=-&quality=85")),
      importance = Major,
      topic = topics
    )
  }

  trait NotificationSenderSupportScope extends Scope {
    var pushSent: Option[Push] = None
    val notificationReport = mock[NotificationReport]
    val notificationSenderSupport = {
      val m = mock[NotificationSenderSupport]
      m.notificationSender returns new NotificationSender {
        override def sendNotification(push: Push): Future[\/[Error, NotificationReport]] = {
          pushSent = Some(push)
          Future.successful(notificationReport.right)
        }
        override def name: String = "test"
      }
    }
  }

  trait NotificationReportRepositorySupportScope extends Scope {
    val notificationReportRepositorySupport = {
      val m = mock[NotificationReportRepositorySupport]
      m.notificationReportRepository returns new InMemoryNotificationReportRepository
    }
  }

  trait MainScope extends Scope with NotificationSenderSupportScope with NotificationReportRepositorySupportScope {
    val apiKey = "test"
    val authenticatedRequest = FakeRequest(method = "POST", path = s"?api-key=$apiKey")
    val conf: Configuration = {
      val m = mock[Configuration]
      m.apiKey returns Some(apiKey)
      m
    }

    val main = new Main(
      configuration = conf,
      notificationSenderSupport = notificationSenderSupport,
      notificationReportRepositorySupport = notificationReportRepositorySupport
    )
  }

}
