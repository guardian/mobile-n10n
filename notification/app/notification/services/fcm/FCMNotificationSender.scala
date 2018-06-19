package notification.services.fcm

import akka.actor.ActorSystem
import com.google.firebase.messaging._
import models.{SenderReport, Topic}
import notification.models.Destination.Destination
import notification.models.Push
import notification.services._
import org.joda.time.DateTime
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class FCMNotificationSender(
  apnsConfigConverter: FCMConfigConverter[ApnsConfig],
  androidConfigConverter: FCMConfigConverter[AndroidConfig],
  firebaseMessaging: FirebaseMessaging,
  fcmExecutionContext: ExecutionContext
)(implicit ec: ExecutionContext) extends NotificationSender {

  private val logger = Logger(classOf[FCMNotificationSender])

  override def sendNotification(push: Push): Future[SenderResult] = {

    val androidConfig = androidConfigConverter.toFCM(push)
    val apnsConfig = apnsConfigConverter.toFCM(push)

    if (androidConfig.isDefined || apnsConfig.isDefined) {
      buildAndSendMessage(push, androidConfig, apnsConfig)
        .recover {
          case NonFatal(exception) =>
            logger.error(s"An error occurred while sending the push notification $push", exception)
            Left(FCMSenderError(Senders.FCM, exception.getMessage))
        }
    } else {
      Future.successful(Left(FCMSenderError(Senders.FCM, "No payload for ios or android to send")))
    }
  }

  def setDestination(messageBuilder: Message.Builder, destination: Destination): Message.Builder = {
    def topicToFirebase(topic: Topic): String = s"${topic.`type`}/${topic.name}".replaceAll("/", "%")
    destination match {
      case Left(topics) if topics.size == 1 => messageBuilder.setTopic(topicToFirebase(topics.head))
      case Left(topics) if topics.size != 1 =>
        messageBuilder.setCondition(topics.map(topic => s"'${topicToFirebase(topic)}' in topics").mkString("||"))
      case Right(token) => messageBuilder.setToken(token.id.toString)
    }
    messageBuilder
  }

  def buildAndSendMessage(push: Push, androidConfig: Option[AndroidConfig], apnsConfig: Option[ApnsConfig]): Future[SenderResult] = {

    val messageBuilder = Message.builder()
      .setNotification(new Notification(push.notification.title, push.notification.message))

    setDestination(messageBuilder, push.destination)

    androidConfig.foreach(messageBuilder.setAndroidConfig)
    apnsConfig.foreach(messageBuilder.setApnsConfig)

    val firebaseNotification = messageBuilder.build

    // FCM's async calls doesn't come with its own thread pool, so we may as well block in a separate thread pool
    Future(firebaseMessaging.send(firebaseNotification))(fcmExecutionContext)
      .map(messageId => Right(SenderReport("FCM", DateTime.now(), sendersId = Some(messageId), None)))

  }

}

case class FCMSenderError(senderName: String, reason: String) extends SenderError