package registration.services

import java.util.UUID

import azure.RawWindowsRegistration.fromMobileRegistration
import azure.{RegistrationResponse => HubRegistrationResponse, RawWindowsRegistration, Tag, NotificationHubClient, WNSRegistrationId}
import models.TopicTypes.{TagContributor, Content, Breaking}
import models.{Topic, Registration, UserId, WindowsMobile}
import org.joda.time.DateTime
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import registration.services.windows.WindowsNotificationRegistrar
import tracking.TopicSubscriptionsRepository

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scalaz.syntax.either._

class WindowsNotificationRegistrarSpec(implicit ev: ExecutionEnv) extends Specification
with Mockito {
  "Windows Notification Provider registration" should {
    "create new registration when no registrations found for channel uri" in new registrations {
      hubClient.create(fromMobileRegistration(registration)) returns Future.successful(hubRegResponse.right)

      val response = provider.register(channelUri, registration)

      response must beEqualTo(registrationResponse.right).await
      there was one(hubClient).create(any[RawWindowsRegistration])
      there was no(hubClient).update(any[WNSRegistrationId], any[RawWindowsRegistration])
      there was no(hubClient).delete(any[WNSRegistrationId])
    }

    "update existing registration when registration with same channel already exist" in new registrations {
      hubClient.registrationsByChannelUri(channelUri) returns Future.successful(List(hubRegResponse).right)
      hubClient.update(hubRegResponse.registration, fromMobileRegistration(registration)) returns Future.successful(hubRegResponse.right)

      val response = provider.register(channelUri, registration)

      response must beEqualTo(registrationResponse.right).await
      there was no(hubClient).create(any[RawWindowsRegistration])
      there was one(hubClient).update(any[WNSRegistrationId], any[RawWindowsRegistration])
      there was no(hubClient).delete(any[WNSRegistrationId])
    }

    "update existing registration when registration with same userId already exist" in new registrations {
      hubClient.registrationsByTag(userIdTag) returns Future.successful(List(hubRegResponse).right)
      hubClient.update(hubRegResponse.registration, fromMobileRegistration(registration)) returns Future.successful(hubRegResponse.right)

      val response = provider.register(channelUri, registration)

      response must beEqualTo(registrationResponse.right).await
      there was no(hubClient).create(any[RawWindowsRegistration])
      there was one(hubClient).update(any[WNSRegistrationId], any[RawWindowsRegistration])
      there was no(hubClient).delete(any[WNSRegistrationId])
    }

    "update existing registration, including channelUri when the registration already exist" in new registrations {
      val lastKnownChannelUri = "lastKnownChannelUri"
      hubClient.registrationsByChannelUri(lastKnownChannelUri) returns Future.successful(List(hubRegResponse.copy(channelUri = lastKnownChannelUri)).right)
      hubClient.registrationsByTag(userIdTag) returns Future.successful(Nil.right)
      hubClient.update(hubRegResponse.registration, fromMobileRegistration(registration)) returns Future.successful(hubRegResponse.right)

      val response = provider.register(lastKnownChannelUri, registration)

      response must beEqualTo(registrationResponse.right).await
      there was no(hubClient).create(any[RawWindowsRegistration])
      there was one(hubClient).update(any[WNSRegistrationId], any[RawWindowsRegistration])
      there was no(hubClient).delete(any[WNSRegistrationId])
    }

    "delete all and replace by only one registration if more than one registration for the same userId" in new registrations {
      val userRegistrations = (0 to 2).map(generateHubResponse).toList
      hubClient.registrationsByTag(userIdTag) returns Future.successful(userRegistrations.right)
      hubClient.delete(any[WNSRegistrationId]) returns Future.successful(().right)
      hubClient.create(fromMobileRegistration(registration)) returns Future.successful(hubRegResponse.right)

      val response = provider.register(channelUri, registration)

      response must beEqualTo(registrationResponse.right).await
      there was one(hubClient).create(any[RawWindowsRegistration])
      there was no(hubClient).update(any[WNSRegistrationId], any[RawWindowsRegistration])
      there was three(hubClient).delete(any[WNSRegistrationId])
    }

    "delete all and replace by only one registration if more than one registration" in new registrations {
      val userRegistrations = (0 to 1).map(generateHubResponse).toList
      hubClient.registrationsByTag(userIdTag) returns Future.successful(userRegistrations.right)
      hubClient.registrationsByChannelUri(registration.deviceId) returns Future.successful(List(generateHubResponse(3)).right)
      hubClient.delete(any[WNSRegistrationId]) returns Future.successful(().right)
      hubClient.create(fromMobileRegistration(registration)) returns Future.successful(hubRegResponse.right)

      val response = provider.register(channelUri, registration)

      response must beEqualTo(registrationResponse.right).await
      there was one(hubClient).create(any[RawWindowsRegistration])
      there was no(hubClient).update(any[WNSRegistrationId], any[RawWindowsRegistration])
      there was exactly(3)(hubClient).delete(any[WNSRegistrationId])
    }

    "track topic subscriptons" in {
      "record added topic subscriptions from new registration" in new registrations {
        hubClient.create(any) returns Future.successful(hubRegResponse.right)

        Await.result(provider.register(channelUri, registrationWithTopics), 5.seconds)

        there was one(topicSubRepo).deviceSubscribed(breakingTopic) andThen one(topicSubRepo).deviceSubscribed(contentTopic)
      }

      "record updated topic subscriptions from existing registration" in new registrations {
        val hubResponseWithTopics = hubRegResponse.copy(tags = Tag.fromTopic(tagTopic).encodedTag :: hubRegResponse.tags)
        hubClient.registrationsByTag(userIdTag) returns Future.successful(List(hubResponseWithTopics).right)
        hubClient.update(hubResponseWithTopics.registration, fromMobileRegistration(registrationWithTopics)) returns Future.successful(hubResponseWithTopics.right)

        Await.result(provider.register(channelUri, registrationWithTopics), 5.seconds)

        there was one(topicSubRepo).deviceSubscribed(breakingTopic) andThen one(topicSubRepo).deviceSubscribed(contentTopic)
        there was one(topicSubRepo).deviceUnsubscribed(tagTopic.id)
      }.pendingUntilFixed
    }
  }

  trait registrations extends Scope {
    def generateHubResponse(i: Int): HubRegistrationResponse = hubRegResponse.copy(channelUri = s"channel$i")

    val userId = UserId(UUID.fromString("988ADFF8-8961-11E5-96E3-D0DB64696656"))
    val userIdTag = Tag.fromUserId(userId).encodedTag

    val registration = Registration("deviceId", WindowsMobile, userId, Set.empty)

    val breakingTopic = Topic(`type` = Breaking, "news")
    val contentTopic = Topic(`type` = Content, "world/news")
    val tagTopic = Topic(`type` = TagContributor, "tag/andrew-sparrow")
    lazy val registrationWithTopics = registration.copy(topics = Set(breakingTopic, contentTopic))

    val channelUri = registration.deviceId

    val hubRegResponse = HubRegistrationResponse(
      registration = WNSRegistrationId("regId"),
      tags = List(s"user:${userId.id.toString}"),
      channelUri = registration.deviceId,
      expirationTime = DateTime.now)

    val registrationResponse = RegistrationResponse(
      deviceId = registration.deviceId,
      platform = WindowsMobile,
      userId = registration.userId,
      topics = Set.empty
    )
    val hubClient = {
      val client = mock[NotificationHubClient]
      client.registrationsByTag(userIdTag) returns Future.successful(Nil.right)
      client.registrationsByChannelUri(registration.deviceId) returns Future.successful(Nil.right)
      client
    }
    val topicSubRepo = mock[TopicSubscriptionsRepository]
    val provider = new WindowsNotificationRegistrar(hubClient, topicSubRepo)
  }

}
