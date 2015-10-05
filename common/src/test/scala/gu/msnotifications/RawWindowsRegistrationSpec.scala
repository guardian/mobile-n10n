package gu.msnotifications

import models.TopicTypes.FootballMatch
import models.{Registration, Topic, UserId, WindowsMobile}
import org.specs2.mutable.Specification

class RawWindowsRegistrationSpec extends Specification {
  "Raw Windows Registration" should {
    "be created from mobile registration" in {
      val userId = UserId("windowsLooser")
      val registration = Registration(
        deviceId = "deviceId",
        platform = WindowsMobile,
        userId = userId,
        topics = Set(Topic(`type` = FootballMatch, name = "arsenal-chelsea"))
      )

      val rawRegistration = RawWindowsRegistration.fromMobileRegistration(registration)

      rawRegistration.channelUri mustEqual registration.deviceId
      rawRegistration.tags mustEqual Tags.withUserId(userId).asSet
    }
  }
}
