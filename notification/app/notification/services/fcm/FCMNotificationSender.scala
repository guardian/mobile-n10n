package notification.services.fcm

import akka.actor.ActorSystem
import com.google.firebase.messaging._
import models.{SenderReport, Topic}
import notification.models.Destination.Destination
import notification.models.Push
import notification.services._
import org.joda.time.DateTime
import play.api.Logger
import com.google.firebase.FirebaseApp

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class FCMNotificationSender(
  apnsConfigConverter: ApnsConfigConverter,
  gcmPushConverter: AndroidConfigConverter,
  firebaseApp: FirebaseApp
)(implicit ec: ExecutionContext, actorSystem: ActorSystem) extends NotificationSender {

  private val logger = Logger(classOf[FCMNotificationSender])

  // FCM calls are blocking, this is to block on a separate thread pool
  private val fcmExecutionContext: ExecutionContext = actorSystem.dispatchers.lookup("fcm-io")

  private implicit class MessageBuilder(messageBuilder: Message.Builder) {
    def setDestination(destination: Destination): Message.Builder = {
      def topicToFirebase(topic: Topic): String = s"${topic.`type`}/${topic.name}".replaceAll("/", "%")
      destination match {
        case Left(topics) if topics.size == 1 => messageBuilder.setTopic(topicToFirebase(topics.head))
        case Left(topics) if topics.size != 1 =>
          messageBuilder.setCondition(topics.map(topic => s"'${topicToFirebase(topic)}' in topics").mkString("||"))
        case Right(token) => messageBuilder.setToken(token.id.toString)
      }
    }
  }

  private def notification(push: Push): Notification = {
    new Notification(push.notification.title, push.notification.message)
  }

  override def sendNotification(push: Push): Future[SenderResult] = {
    val messageBuilder = Message.builder()
      .setNotification(notification(push))
      .setDestination(push.destination)

    apnsConfigConverter.toIosConfig(push).foreach(messageBuilder.setApnsConfig)
    gcmPushConverter.toAndroidConfig(push).foreach(messageBuilder.setAndroidConfig)

    val message = messageBuilder.build()

    // FCM's async calls doesn't come with its own thread pool, so we may as well block in a separate thread pool
    Future(FirebaseMessaging.getInstance(firebaseApp).send(message))(fcmExecutionContext)
      .map(messageId => Right(SenderReport("FCM", DateTime.now(), sendersId = Some(messageId), None)))
      .recover {
        case NonFatal(exception) =>
          logger.error(s"An error occurred while sending the push notification $push", exception)
          Left(NotificationRejected(Some(FCMSenderError("FCM", exception.getMessage))))
      }
  }

  case class FCMSenderError(senderName: String, reason: String) extends SenderError
}
