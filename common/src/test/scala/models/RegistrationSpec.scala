 package models

import models.TopicTypes.{Breaking, FootballMatch}
import org.specs2.mutable.Specification
import play.api.libs.json.Json

class RegistrationSpec extends Specification {
  "Registration" should {
    "parse a valid registration" in {
      val json = Json.parse(
        """{"deviceToken":"abc","platform":"ios","topics":[{"type":"breaking","name":"uk"}]}"""
      )
      val result = json.validate[Registration]
      result.isSuccess must beTrue
      result.get.topics must_== Set(Topic(Breaking, "uk"))
    }

    "fail for an unknown topic type" in {
      val json = Json.parse(
        """{"deviceToken":"abc","platform":"ios","topics":[{"type":"tag","name":"some-tag"},{"type":"breaking","name":"uk"}]}"""
      )
      json.validate[Registration].isError must beTrue
    }

    "still fail for other invalid fields" in {
      val json = Json.parse(
        """{"deviceToken":"abc","platform":"unknown-platform","topics":[{"type":"breaking","name":"uk"}]}"""
      )
      json.validate[Registration].isError must beTrue
    }
  }
}