package notification.services


import azure.{AzureRawPush, NotificationHubClient}
import models._
import models.Importance.{Minor, Major}
import notification.{DateTimeFreezed, NotificationsFixtures}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import tracking.TopicSubscriptionsRepository
import scalaz.syntax.either._
import org.specs2.concurrent.ExecutionEnv

import scala.concurrent.Future
import scalaz.syntax.std.option._

class WindowsNotificationSenderSpec(implicit ev: ExecutionEnv) extends Specification
  with Mockito with DateTimeFreezed {

  "the notification sender" should {
    "filter out Minor notifications" in new WNSScope() {
      override val importance = Minor
      val expectedReport = senderReport(Senders.Windows).right
      val result = windowsNotificationSender.sendNotification(somePush)

      result should beEqualTo(expectedReport).await
      there was no(hubClient).sendNotification(any[AzureRawPush])
    }
    "process a Major notification" in new WNSScope() {
      val result = windowsNotificationSender.sendNotification(somePush)

      result should beEqualTo(senderReport(Senders.Windows, platformStats = PlatformStatistics(WindowsMobile, 1).some).right).await
      there was one(hubClient).sendNotification(any[AzureRawPush])
    }
  }

  trait WNSScope extends Scope with NotificationsFixtures {
    def importance: Importance = Major
    val somePush = breakingNewsPush(importance)

    val configuration = mock[Configuration].debug returns true
    val hubClient = {
      val client = mock[NotificationHubClient]
      client.sendNotification(any[AzureRawPush]) returns Future.successful(().right)
      client
    }
    val windowsNotificationSender = new WindowsNotificationSender(hubClient, configuration, mock[TopicSubscriptionsRepository])
  }
}
