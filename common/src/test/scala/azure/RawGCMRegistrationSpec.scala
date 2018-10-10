package azure

import models.TopicTypes.FootballMatch
import models._
import org.specs2.mutable.Specification

class RawGCMRegistrationSpec extends Specification {

  "Raw GCM Registration" should {

    "be created from mobile registration with topics as tags" in {
      val topic = Topic(`type` = FootballMatch, "arsenal-chelsea")
      val registration = Registration(
        deviceToken = FcmToken("device2"),
        platform = Android,
        topics = Set(topic),
        buildTier = None,
        provider = None
      )

      val rawRegistration = RawGCMRegistration.fromMobileRegistration(registration)

      rawRegistration.tags must contain(Tag.fromTopic(topic).encodedTag)
    }

    "throw an exception if trying to create a registration with an azure token" in {
      val topic = Topic(`type` = FootballMatch, "arsenal-chelsea")
      val registration = Registration(
        deviceToken = AzureToken("device2"),
        platform = Android,
        topics = Set(topic),
        buildTier = None,
        provider = None
      )

      RawGCMRegistration.fromMobileRegistration(registration) should throwA[RuntimeException]
    }
  }

}
