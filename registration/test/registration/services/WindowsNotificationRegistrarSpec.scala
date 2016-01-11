package registration.services

import java.util.UUID

import azure.RawWindowsRegistration.fromMobileRegistration
import azure.{NotificationHubClient, RegistrationResponse => HubRegistrationResponse, WNSRegistrationId}
import models.{Registration, UserId, WindowsMobile}
import org.joda.time.DateTime
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.Future
import scalaz.syntax.either._

class WindowsNotificationRegistrarSpec(implicit ev: ExecutionEnv) extends Specification
with Mockito {
  "Windows Notification Provider registration" should {
    "create new registration when no registrations found for channel uri" in new registrations {
      val channelUri = registration.deviceId
      hubClient.registrationsByChannelUri(channelUri) returns Future.successful(List.empty.right)
      hubClient.create(fromMobileRegistration(registration)) returns Future.successful(hubRegResponse.right)

      val response = provider.register(channelUri, registration)

      response must beEqualTo(RegistrationResponse(
        deviceId = channelUri,
        platform = WindowsMobile,
        userId = registration.userId,
        topics = Set.empty
      ).right).await
    }

    "update existing registration when registration already exist" in new registrations {
      val channelUri = registration.deviceId
      hubClient.registrationsByChannelUri(channelUri) returns Future.successful(List(hubRegResponse).right)
      hubClient.update(hubRegResponse.registration, fromMobileRegistration(registration)) returns Future.successful(hubRegResponse.right)

      val response = provider.register(channelUri, registration)

      response must beEqualTo(RegistrationResponse(
        deviceId = channelUri,
        platform = WindowsMobile,
        userId = registration.userId,
        topics = Set.empty
      ).right).await
    }

    "update existing registration, including channelUri when the registration already exist" in new registrations {
      val channelUri = hubRegResponse.channelUri
      val lastKnownChannelUri = "lastKnownChannelUri"
      hubClient.registrationsByChannelUri(lastKnownChannelUri) returns Future.successful(List(hubRegResponse.copy(channelUri = lastKnownChannelUri)).right)
      hubClient.registrationsByChannelUri(channelUri) returns Future.successful(Nil.right)
      hubClient.update(hubRegResponse.registration, fromMobileRegistration(registration)) returns Future.successful(hubRegResponse.right)

      val response = provider.register(lastKnownChannelUri, registration)

      response must beEqualTo(RegistrationResponse(
        deviceId = channelUri,
        platform = WindowsMobile,
        userId = registration.userId,
        topics = Set.empty
      ).right).await
    }

    "update existing registration, if the target registration already exists (instead of creating a new one)" in new registrations {
      val channelUri = hubRegResponse.channelUri
      val lastKnownChannelUri = "lastKnownChannelUri"
      hubClient.registrationsByChannelUri(lastKnownChannelUri) returns Future.successful(List(hubRegResponse.copy(channelUri = lastKnownChannelUri)).right)
      hubClient.registrationsByChannelUri(channelUri) returns Future.successful(List(hubRegResponse).right)
      hubClient.update(hubRegResponse.registration, fromMobileRegistration(registration)) returns Future.successful(hubRegResponse.right)

      val response = provider.register(lastKnownChannelUri, registration)

      response must beEqualTo(RegistrationResponse(
        deviceId = channelUri,
        platform = WindowsMobile,
        userId = registration.userId,
        topics = Set.empty
      ).right).await
    }

  }

  trait registrations extends Scope {
    val hubClient = mock[NotificationHubClient]
    val provider = new WindowsNotificationRegistrar(hubClient)

    val userId = UserId(UUID.fromString("988ADFF8-8961-11E5-96E3-D0DB64696656"))

    val registration = Registration("deviceId", WindowsMobile, userId, Set.empty)
    val hubRegResponse = HubRegistrationResponse(
      registration = WNSRegistrationId("regId"),
      tags = List(s"user:${userId.id.toString}"),
      channelUri = registration.deviceId,
      expirationTime = DateTime.now)
  }

}
