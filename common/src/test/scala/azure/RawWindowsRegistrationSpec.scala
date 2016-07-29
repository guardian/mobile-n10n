package azure

import java.util.UUID

import models.TopicTypes.FootballMatch
import models.{Topic, Registration, UniqueDeviceIdentifier, WindowsMobile}
import org.specs2.mutable.Specification

class RawWindowsRegistrationSpec extends Specification {

  "Raw Windows Registration" should {
    val userId = UniqueDeviceIdentifier(UUID.fromString("988ADFF8-8961-11E5-96E3-D0DB64696656"))

    "be created from mobile registration with user tag without topics" in {
      val registration = Registration(
        deviceId = "deviceId",
        platform = WindowsMobile,
        udid = userId,
        topics = Set.empty
      )

      val rawRegistration = RawWindowsRegistration.fromMobileRegistration(registration)

      rawRegistration.channelUri mustEqual registration.deviceId
      rawRegistration.tags must contain(exactly(s"user:${userId.id.toString}"))
    }

    "be created from mobile registration with topics as tags" in {
      val topic = Topic(`type` = FootballMatch, "arsenal-chelsea")
      val registration = Registration(
        deviceId = "device2",
        platform = WindowsMobile,
        udid = userId,
        topics = Set(topic)
      )

      val rawRegistration = RawWindowsRegistration.fromMobileRegistration(registration)

      rawRegistration.tags must contain(Tag.fromTopic(topic).encodedTag)
    }
  }

}
