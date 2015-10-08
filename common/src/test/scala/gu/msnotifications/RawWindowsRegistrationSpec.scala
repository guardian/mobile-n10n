package gu.msnotifications

import models.TopicTypes.FootballMatch
import models.{Topic, Registration, UserId, WindowsMobile}
import org.specs2.mutable.Specification

class RawWindowsRegistrationSpec extends Specification {

  "Raw Windows Registration" should {
    val userId = UserId("windowsLooser")

    "be created from mobile registration with user tag without topics" in {
      val registration = Registration(
        deviceId = "deviceId",
        platform = WindowsMobile,
        userId = userId,
        topics = Set.empty
      )

      val rawRegistration = RawWindowsRegistration.fromMobileRegistration(registration)

      rawRegistration.channelUri mustEqual registration.deviceId
      rawRegistration.tags must contain(exactly("user:windowsLooser"))
    }
    "be created from mobile registration with topics as tags" in {
      val registration = Registration(
        deviceId = "device2",
        platform = WindowsMobile,
        userId = userId,
        topics = Set(Topic(`type` = FootballMatch, "arsenal-chelsea"))
      )

      val rawRegistration = RawWindowsRegistration.fromMobileRegistration(registration)

      rawRegistration.tags must contain("arsenal-chelsea")
    }
  }

}
