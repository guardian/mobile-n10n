package notification.services

import java.util.UUID

import azure.{AzureRawPush, NotificationHubClient}
import models._
import models.Importance.{Minor, Major}
import models.Link.Internal
import notification.models.Push
import org.joda.time.{DateTime, DateTimeUtils}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.{BeforeEach, AfterEach, Scope}
import scalaz.syntax.either._
import org.specs2.concurrent.ExecutionEnv

import scala.concurrent.Future

class WindowsNotificationSenderSpec(implicit ev: ExecutionEnv) extends Specification with Mockito with AfterEach with BeforeEach {

  override def after: Unit = DateTimeUtils.setCurrentMillisSystem()
  override def before: Unit = DateTimeUtils.setCurrentMillisFixed(0L)

  "the notification sender" should {
    "filter out Minor notifications" in new WNSScope() {
      override val importance = Minor
      val expectedReport = notificationReport(stats = Map.empty).right
      val result = windowsNotificationSender.sendNotification(somePush)
      result should beEqualTo(expectedReport).await
      there was no(hubClient).sendNotification(any[AzureRawPush])
    }
    "process a Major notification" in new WNSScope() {
      val result = windowsNotificationSender.sendNotification(somePush)
      result should beEqualTo(notificationReport().right).await
      there was one(hubClient).sendNotification(any[AzureRawPush])
    }
  }

  trait WNSScope extends Scope {
    def importance: Importance = Major

    val somePush: Push = Push(
      notification = BreakingNewsNotification(
        id = UUID.randomUUID(),
        title = "",
        message = "",
        thumbnailUrl = None,
        sender = "test",
        link = Internal("capiId"),
        imageUrl = None,
        importance = importance,
        topic = Set()
      ),
      destination = Right(UserId(UUID.randomUUID()))
    )

    def notificationReport(stats: Map[Platform, Option[Int]] = Map(WindowsMobile -> Some(1))): NotificationReport = NotificationReport.create(
      sentTime = DateTime.now,
      notification = somePush.notification,
      statistics = NotificationStatistics(stats)
    )

    val configuration = mock[Configuration].debug returns true
    val hubClient = {
      val client = mock[NotificationHubClient]
      client.sendNotification(any[AzureRawPush]) returns Future.successful(().right)
      client
    }
    val windowsNotificationSender = new WindowsNotificationSender(hubClient, configuration)
  }
}
