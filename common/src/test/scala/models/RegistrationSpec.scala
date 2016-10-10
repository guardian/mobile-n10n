package models

import java.util.UUID

import org.specs2.mutable.Specification
import play.api.libs.json.Json

class RegistrationSpec extends Specification {
  "Registration" should {
    "parse json representation" in {
      val json =
        """{
          |  "deviceId": "some-device-id",
          |  "platform": "windows-mobile",
          |  "udid": "aaaa4696-5568-11e6-beb8-9e71128cae77",
          |  "topics": [{
          |    "type": "breaking",
          |    "name": "uk"
          |  }]
          |}
        """.stripMargin
      val expected = Registration(
        deviceId = "some-device-id",
        platform = WindowsMobile,
        udid = UniqueDeviceIdentifier(UUID.fromString("aaaa4696-5568-11e6-beb8-9e71128cae77")),
        topics = Set(Topic(TopicTypes.Breaking, "uk")),
        buildTier = None
      )

      Json.parse(json).as[Registration] mustEqual expected
    }

    "parse userId as udid" in {
      val json =
        """{
          |  "deviceId": "some-device-id",
          |  "platform": "windows-mobile",
          |  "userId": "aaaa4696-5568-11e6-beb8-9e71128cae77",
          |  "topics": [{
          |    "type": "breaking",
          |    "name": "uk"
          |  }]
          |}
        """.stripMargin
      val expected = Registration(
        deviceId = "some-device-id",
        platform = WindowsMobile,
        udid = UniqueDeviceIdentifier(UUID.fromString("aaaa4696-5568-11e6-beb8-9e71128cae77")),
        topics = Set(Topic(TopicTypes.Breaking, "uk")),
        buildTier = None
      )

      Json.parse(json).as[Registration] mustEqual expected
    }
    "prefer udid if both udid and userId are present" in {
      val json =
        """{
          |  "deviceId": "some-device-id",
          |  "platform": "windows-mobile",
          |  "udid": "aaaa4696-5568-11e6-beb8-9e71128cae77",
          |  "userId": "00000000-0000-0000-0000-000000000000",
          |  "topics": [{
          |    "type": "breaking",
          |    "name": "uk"
          |  }]
          |}
        """.stripMargin
      val expected = Registration(
        deviceId = "some-device-id",
        platform = WindowsMobile,
        udid = UniqueDeviceIdentifier(UUID.fromString("aaaa4696-5568-11e6-beb8-9e71128cae77")),
        topics = Set(Topic(TopicTypes.Breaking, "uk")),
        buildTier = None
      )

      Json.parse(json).as[Registration] mustEqual expected
    }

  }
}
