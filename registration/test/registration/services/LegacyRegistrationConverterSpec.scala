package registration.services

import error.MalformattedRegistration
import models.Provider.Azure
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
          firebaseToken = Some("def"),
          platform = "android"
        )
      )
      val expectedRegistration = expectedDefaultRegistration.copy(
        platform = Android,
        deviceToken = DeviceToken("def")
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
        deviceToken = DeviceToken("abc")
      )
      val registration = legacyRegistrationConverter.toRegistration(legacyRegistration)

      registration should beRight(expectedRegistration)
    }

    //This is for this specific issue: https://theguardian.atlassian.net/browse/MSS-609
    "Return an error where an android device has no fcm token " in new LegacyRegistrationConverterScope {
        val legacyRegistration = defaultLegacyRegistration.copy(
          device = defaultLegacyRegistration.device.copy(
            platform = "android"
          )
        )

        val expectedRegistrationError = MalformattedRegistration("Android device without firebase registration token")

        val registrationError = legacyRegistrationConverter.toRegistration(legacyRegistration)

        registrationError should beLeft(expectedRegistrationError)
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
        topics = None,
        provider = Some(Azure),
      )
    )
    val expectedDefaultRegistration = Registration(
      deviceToken = DeviceToken("abc"),
      platform = Ios,
      topics = Set(Topic(TopicTypes.Breaking, "uk")),
      buildTier = Some("test"),
      appVersion = None
    )
  }
}
