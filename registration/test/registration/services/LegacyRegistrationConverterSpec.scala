package registration.services

import models._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import registration.models.{LegacyDevice, LegacyPreferences, LegacyRegistration}

class LegacyRegistrationConverterSpec extends Specification {
  "LegacyRegistrationConverter" should {
    "Convert a legacy registration to an internal one" in new LegacyRegistrationConverterScope {
      val registration = legacyRegistrationConverter.toRegistration(defaultLegacyRegistration)

      registration should beRight(expectedDefaultRegistration)
    }
    "Convert a legacy registration with a firebase token only to an internal one" in new LegacyRegistrationConverterScope {
      val legacyRegistration = defaultLegacyRegistration.copy(
        device = defaultLegacyRegistration.device.copy(
          pushToken = None,
          firebaseToken = Some("def")
        )
      )
      val expectedRegistration = expectedDefaultRegistration.copy(
        deviceToken = FcmToken("def")
      )
      val registration = legacyRegistrationConverter.toRegistration(legacyRegistration)

      registration should beRight(expectedRegistration)
    }
    "Convert a legacy registration with both a firebase token and an azure token to an internal one" in new LegacyRegistrationConverterScope {
      val legacyRegistration = defaultLegacyRegistration.copy(
        device = defaultLegacyRegistration.device.copy(
          pushToken = Some("abc"),
          firebaseToken = Some("def")
        )
      )
      val expectedRegistration = expectedDefaultRegistration.copy(
        deviceToken = BothTokens("abc", "def")
      )
      val registration = legacyRegistrationConverter.toRegistration(legacyRegistration)

      registration should beRight(expectedRegistration)
    }
  }

  trait LegacyRegistrationConverterScope extends Scope {
    val legacyRegistrationConverter = new LegacyRegistrationConverter()
    val defaultLegacyRegistration = LegacyRegistration(
      device = LegacyDevice(
        platform = "ios",
        pushToken = Some("abc"),
        firebaseToken = None,
        buildTier = "test"
      ),
      preferences = LegacyPreferences(
        receiveNewsAlerts = true,
        edition = "uk",
        teams = None,
        matches = None,
        topics = None
      )
    )
    val expectedDefaultRegistration = Registration(
      deviceToken = AzureToken("abc"),
      platform = iOS,
      topics = Set(Topic(TopicTypes.Breaking, "uk")),
      buildTier = Some("test")
    )
  }
}
