package azure

import java.util.UUID

import models.TopicTypes.FootballMatch
import models.{Android, Registration, Topic, UniqueDeviceIdentifier}
import org.specs2.mutable.Specification

class RawGCMRegistrationSpec extends Specification {

  "Raw GCM Registration" should {
    val userId = UniqueDeviceIdentifier(UUID.fromString("988ADFF8-8961-11E5-96E3-D0DB64696656"))

    "be created from mobile registration with user tag without topics" in {
      val registration = Registration(
        deviceId = "deviceId",
        platform = Android,
        udid = userId,
        topics = Set.empty,
        buildTier = None
      )

      val rawRegistration = RawGCMRegistration.fromMobileRegistration(registration)

      rawRegistration.gcmRegistrationId mustEqual registration.deviceId
      rawRegistration.tags must contain(exactly(s"user:${userId.id.toString}"))
    }

    "be created from mobile registration with topics as tags" in {
      val topic = Topic(`type` = FootballMatch, "arsenal-chelsea")
      val registration = Registration(
        deviceId = "device2",
        platform = Android,
        udid = userId,
        topics = Set(topic),
        buildTier = None
      )

      val rawRegistration = RawGCMRegistration.fromMobileRegistration(registration)

      rawRegistration.tags must contain(Tag.fromTopic(topic).encodedTag)
    }
  }

}
