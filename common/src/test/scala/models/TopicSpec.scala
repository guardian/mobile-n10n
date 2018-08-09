package models

import models.TopicTypes.TagContributor
import org.specs2.mutable.Specification

class TopicSpec extends Specification {
  "Topic" should {
    "be able to parse a topic formatted with slashes" in {
      val parsedTopic = Topic.fromString("tag-contributor/profile/robertbooth")
      parsedTopic should beRight(Topic(TagContributor, "profile/robertbooth"))
    }
    "be able to parse a topic formatted with percents" in {
      val parsedTopic = Topic.fromString("tag-contributor%profile%robertbooth")
      parsedTopic should beRight(Topic(TagContributor, "profile/robertbooth"))
    }
  }
}
