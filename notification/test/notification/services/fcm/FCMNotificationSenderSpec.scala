package notification.services.fcm

import java.util.UUID

import com.google.firebase.messaging.{AndroidConfig, ApnsConfig, FirebaseMessaging, Message}
import models._
import models.Importance.Major
import models.Link.Internal
import models.TopicTypes.Breaking
import notification.models.Push
import notification.services.Senders
import org.mockito.ArgumentCaptor
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.Future

class FCMNotificationSenderSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {
  "FCMNotificationSender" should {
    "Not send a notification if there's neither an iOS nor an Android payload" in new FCMNotificationSenderScope {
      val result = fcmNotificationSender().sendNotification(push)

      result should beEqualTo(Left(FCMSenderError(Senders.FCM, "No payload for ios or android to send"))).await
      there was no(firebaseMessaging).send(any)
    }

    "Send a notification if an iOS or Android config is present" in new FCMNotificationSenderScope {
      val result = fcmNotificationSender(androidConfig = Some(mock[AndroidConfig])).sendNotification(push)

      result should beRight(haveClass[SenderReport]).await
      there was one(firebaseMessaging).send(any)
    }

    "Set the topic attribute if the destination is only one topic" in new FCMNotificationSenderScope {
      val destination = List(Topic(Breaking, "uk"))

      val messageBuilder = mock[Message.Builder]
      fcmNotificationSender(androidConfig = Some(mock[AndroidConfig])).setDestination(messageBuilder, destination)

      val topicCaptor = ArgumentCaptor.forClass(classOf[String])
      there was one(messageBuilder).setTopic(topicCaptor.capture())
      topicCaptor.getValue shouldEqual "breaking%uk"
      there was no(messageBuilder).setCondition(any)
      there was no(messageBuilder).setToken(any)
    }

    "Set the condition attribute if the destination is more than one topic" in new FCMNotificationSenderScope {
      val destination = List(Topic(Breaking, "uk"), Topic(Breaking, "us"))

      val messageBuilder = mock[Message.Builder]
      fcmNotificationSender(androidConfig = Some(mock[AndroidConfig])).setDestination(messageBuilder, destination)

      val conditionCaptor = ArgumentCaptor.forClass(classOf[String])
      there was no(messageBuilder).setTopic(any)
      there was one(messageBuilder).setCondition(conditionCaptor.capture())
      conditionCaptor.getValue shouldEqual "'breaking%uk' in topics||'breaking%us' in topics"
      there was no(messageBuilder).setToken(any)
    }
  }

  trait FCMNotificationSenderScope extends Scope {

    def apnsConfigConverter(apnsConfig: Option[ApnsConfig]) = new FCMConfigConverter[ApnsConfig] {
      override def toFCM(push: Push): Option[ApnsConfig] = apnsConfig
    }
    def androidConfigConverter(androidConfig: Option[AndroidConfig]) = new FCMConfigConverter[AndroidConfig] {
      override def toFCM(push: Push): Option[AndroidConfig] = androidConfig
    }

    val firebaseMessaging = mock[FirebaseMessaging]
    firebaseMessaging.send(any[Message]) returns ""

    def fcmNotificationSender(
      apnsConfig: Option[ApnsConfig] = None,
      androidConfig: Option[AndroidConfig] = None
    ): FCMNotificationSender = new FCMNotificationSender(
      apnsConfigConverter = apnsConfigConverter(apnsConfig),
      androidConfigConverter = androidConfigConverter(androidConfig),
      firebaseMessaging = firebaseMessaging,
      fcmExecutionContext = ee.ec
    )

    val push = Push(
      notification = BreakingNewsNotification(
        id = UUID.randomUUID(),
        title = "EU to consider plans for migrant processing centres in north Africa",
        message = "Leaked draft document for upcoming summit says idea could ‘reduce incentive for perilous journeys’",
        thumbnailUrl = None,
        sender = "Unit tests",
        link = Internal("world/2018/jun/19/eu-migrant-processing-centres-north-africa-refugees", None, GITContent),
        imageUrl = None,
        importance = Major,
        topic = Set()
      ),
      destination = Set(Topic(`type` = TopicTypes.Breaking, name = "uk"))
    )
  }
}
