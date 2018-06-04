package notification.services.fcm

import com.google.firebase.messaging._
import models.SenderReport
import notification.models.Destination.Destination
import notification.models.Push
import notification.services.{NotificationRejected, NotificationSender, SenderError, SenderResult}
import org.joda.time.DateTime
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class FCMNotificationSender(apnsConfigConverter: ApnsConfigConverter, gcmPushConverter: AndroidConfigConverter)(fcmExecutionContext: ExecutionContext) extends NotificationSender {

  private val logger = Logger(classOf[FCMNotificationSender])

  private implicit class MessageBuilder(messageBuilder: Message.Builder) {
    def setDestination(destination: Destination): Message.Builder = destination match {
      case Left(topics) if topics.size == 1 => messageBuilder.setTopic(topics.head.toString)
      case Left(topics) if topics.size != 1 => messageBuilder.setCondition(topics.map(topic => s"'$topic' in topics").mkString("||"))
      case Right(token) => messageBuilder.setToken(token.id.toString)
    }
  }

  private def notification(push: Push): Notification = {
    new Notification(push.notification.title, push.notification.message)
  }

  override def sendNotification(push: Push): Future[SenderResult] = {
    val message = Message.builder()
      .setApnsConfig(apnsConfigConverter.toIosConfig(push))
      .setAndroidConfig(gcmPushConverter.toAndroidConfig(push))
      .setNotification(notification(push))
      .setDestination(push.destination)
      .build()


    // FCM's async calls doesn't come with its own thread pool, so we may as well block in a separate thread pool
    Future(FirebaseMessaging.getInstance().send(message))(fcmExecutionContext)
      .map(messageId => Right(SenderReport("FCM", DateTime.now(), sendersId = Some(messageId), None)))
      .recover {
        case NonFatal(exception) =>
          logger.error(s"An error occurred while sending the push notification $push", exception)
          Left(NotificationRejected(Some(FCMSenderError("FCM", exception.getMessage))))
      }
  }

  case class FCMSenderError(senderName: String, reason: String) extends SenderError
}
