package registration.services

import java.util.UUID

import _root_.azure.RawWindowsRegistration.fromMobileRegistration
import _root_.azure.{NotificationHubClient, NotificationHubRegistrationId, RawWindowsRegistration, Tag, WNSRegistrationResponse}
import models.TopicTypes.{Breaking, Content, TagContributor}
import models.{Registration, Topic, UniqueDeviceIdentifier, WindowsMobile}
import org.joda.time.DateTime
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import azure.{WindowsNotificationRegistrar}
import _root_.azure.Registrations
import tracking.{SubscriptionTracker, TopicSubscriptionsRepository}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import cats.implicits._

class WindowsNotificationRegistrarSpec(implicit ev: ExecutionEnv) extends Specification
with Mockito {
  "Windows Notification Provider registration" should {
    "create new registration when no registrations found for channel uri" in new registrations {
      hubClient.create(fromMobileRegistration(registration)) returns Future.successful(hubRegResponse.right)

      val response = provider.register(channelUri, registration)

      response must beEqualTo(registrationResponse.right).await
      there was one(hubClient).create(any[RawWindowsRegistration])
      there was no(hubClient).update(any[NotificationHubRegistrationId], any[RawWindowsRegistration])
      there was no(hubClient).delete(any[NotificationHubRegistrationId])
    }

    "update existing registration when registration with same channel already exist" in new registrations {
      hubClient.registrationsByChannelUri(channelUri) returns Future.successful(List(hubRegResponse).right)
      hubClient.update(hubRegResponse.registration, fromMobileRegistration(registration)) returns Future.successful(hubRegResponse.right)

      val response = provider.register(channelUri, registration)

      response must beEqualTo(registrationResponse.right).await
      there was no(hubClient).create(any[RawWindowsRegistration])
      there was one(hubClient).update(any[NotificationHubRegistrationId], any[RawWindowsRegistration])
      there was no(hubClient).delete(any[NotificationHubRegistrationId])
    }

    "update existing registration when registration with same userId already exist" in new registrations {
      hubClient.registrationsByTag(userIdTag) returns Future.successful(Registrations(List(hubRegResponse), None).right)
      hubClient.update(hubRegResponse.registration, fromMobileRegistration(registration)) returns Future.successful(hubRegResponse.right)

      val response = provider.register(channelUri, registration)

      response must beEqualTo(registrationResponse.right).await
      there was no(hubClient).create(any[RawWindowsRegistration])
      there was one(hubClient).update(any[NotificationHubRegistrationId], any[RawWindowsRegistration])
      there was no(hubClient).delete(any[NotificationHubRegistrationId])
    }

    "update existing registration, including channelUri when the registration already exist" in new registrations {
      val lastKnownChannelUri = "lastKnownChannelUri"
      hubClient.registrationsByChannelUri(lastKnownChannelUri) returns Future.successful(List(hubRegResponse.copy(channelUri = lastKnownChannelUri)).right)
      hubClient.registrationsByTag(userIdTag) returns Future.successful(Registrations(Nil, None).right)
      hubClient.update(hubRegResponse.registration, fromMobileRegistration(registration)) returns Future.successful(hubRegResponse.right)

      val response = provider.register(lastKnownChannelUri, registration)

      response must beEqualTo(registrationResponse.right).await
      there was no(hubClient).create(any[RawWindowsRegistration])
      there was one(hubClient).update(any[NotificationHubRegistrationId], any[RawWindowsRegistration])
      there was no(hubClient).delete(any[NotificationHubRegistrationId])
    }

    "delete a registration" in new registrations {
      val userRegistrations = (0 to 2).map(generateHubResponse).toList
      hubClient.registrationsByTag(userIdTag) returns Future.successful(Registrations(userRegistrations, None).right)
      hubClient.delete(any[NotificationHubRegistrationId]) returns Future.successful(().right)

      val response = provider.unregister(registration.udid)

      response must beEqualTo(().right).await
      there was no(hubClient).create(any[RawWindowsRegistration])
      there was three(hubClient).delete(any[NotificationHubRegistrationId])
    }

    "delete all and replace by only one registration if more than one registration for the same userId" in new registrations {
      val userRegistrations = (0 to 2).map(generateHubResponse).toList
      hubClient.registrationsByTag(userIdTag) returns Future.successful(Registrations(userRegistrations, None).right)
      hubClient.delete(any[NotificationHubRegistrationId]) returns Future.successful(().right)
      hubClient.create(fromMobileRegistration(registration)) returns Future.successful(hubRegResponse.right)

      val response = provider.register(channelUri, registration)

      response must beEqualTo(registrationResponse.right).await
      there was one(hubClient).create(any[RawWindowsRegistration])
      there was no(hubClient).update(any[NotificationHubRegistrationId], any[RawWindowsRegistration])
      there was three(hubClient).delete(any[NotificationHubRegistrationId])
    }

    "delete all and replace by only one registration if more than one registration" in new registrations {
      val userRegistrations = (0 to 1).map(generateHubResponse).toList
      hubClient.registrationsByTag(userIdTag) returns Future.successful(Registrations(userRegistrations, None).right)
      hubClient.registrationsByChannelUri(registration.deviceId) returns Future.successful(List(generateHubResponse(3)).right)
      hubClient.delete(any[NotificationHubRegistrationId]) returns Future.successful(().right)
      hubClient.create(fromMobileRegistration(registration)) returns Future.successful(hubRegResponse.right)

      val response = provider.register(channelUri, registration)

      response must beEqualTo(registrationResponse.right).await
      there was one(hubClient).create(any[RawWindowsRegistration])
      there was no(hubClient).update(any[NotificationHubRegistrationId], any[RawWindowsRegistration])
      there was exactly(3)(hubClient).delete(any[NotificationHubRegistrationId])
    }

    "track topic subscriptons" in {
      "record added topic subscriptions from new registration" in new registrations {
        hubClient.create(any) returns Future.successful(hubRegResponse.right)

        Await.result(provider.register(channelUri, registrationWithTopics), 5.seconds)

        there was one(topicSubRepo).deviceSubscribed(breakingTopic, 1) andThen one(topicSubRepo).deviceSubscribed(contentTopic, 1)
      }

      "record updated topic subscriptions from existing registration" in new registrations {
        val hubResponseWithTopics = hubRegResponse.copy(tags = Tag.fromTopic(tagTopic).encodedTag :: hubRegResponse.tags)
        hubClient.registrationsByTag(userIdTag) returns Future.successful(Registrations(List(hubResponseWithTopics), None).right)
        hubClient.update(hubResponseWithTopics.registration, fromMobileRegistration(registrationWithTopics)) returns Future.successful(hubResponseWithTopics.right)

        Await.result(provider.register(channelUri, registrationWithTopics), 5.seconds)

        there was one(topicSubRepo).deviceSubscribed(breakingTopic, 1) andThen one(topicSubRepo).deviceSubscribed(contentTopic, 1)
        there was one(topicSubRepo).deviceUnsubscribed(tagTopic.id, 1)
      }
    }
  }

  trait registrations extends Scope {
    def generateHubResponse(i: Int): WNSRegistrationResponse = hubRegResponse.copy(channelUri = s"channel$i")

    val userId = UniqueDeviceIdentifier(UUID.fromString("988ADFF8-8961-11E5-96E3-D0DB64696656"))
    val userIdTag = Tag.fromUserId(userId).encodedTag

    val registration = Registration("deviceId", WindowsMobile, userId, Set.empty, None)

    val breakingTopic = Topic(`type` = Breaking, "news")
    val contentTopic = Topic(`type` = Content, "world/news")
    val tagTopic = Topic(`type` = TagContributor, "tag/andrew-sparrow")
    lazy val registrationWithTopics = registration.copy(topics = Set(breakingTopic, contentTopic))

    val channelUri = registration.deviceId

    val hubRegResponse = WNSRegistrationResponse(
      registration = NotificationHubRegistrationId("regId"),
      tags = List(s"user:${userId.id.toString}"),
      channelUri = registration.deviceId,
      expirationTime = DateTime.now)

    val registrationResponse = RegistrationResponse(
      deviceId = registration.deviceId,
      platform = WindowsMobile,
      userId = registration.udid,
      topics = Set.empty
    )
    val hubClient = {
      val client = mock[NotificationHubClient]
      client.registrationsByTag(userIdTag) returns Future.successful(Registrations(Nil, None).right)
      client.registrationsByChannelUri(registration.deviceId) returns Future.successful(Nil.right)
      client
    }
    val topicSubRepo = mock[TopicSubscriptionsRepository]
    val provider = new WindowsNotificationRegistrar(hubClient, new SubscriptionTracker(topicSubRepo))
  }

}
