package notification.services.fcm

import com.google.firebase.messaging.Message
import models.ContentNotification
import notification.models.Push
import notification.services.{NotificationSender, SenderResult}

import scala.concurrent.Future

class FCMNotificationSender extends NotificationSender {

  private implicit class MessageBuilder(messageBuilder: Message.Builder) {
    def setTopics(topics: Set[String]): Message.Builder = {
      if (topics.size == 1) {
        messageBuilder.setTopic(topics.head)
      } else {
        messageBuilder.setCondition(topics.map(topic => s"'$topic' in topics").mkString("||"))
      }
    }
  }

  override def sendNotification(push: Push): Future[SenderResult] = {
    Message.builder()
      .setAndroidConfig()
      .setApnsConfig()
      .setNotification()
      .setTopics(push.destination)

  }
}
