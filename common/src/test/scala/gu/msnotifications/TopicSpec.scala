package gu.msnotifications

import org.scalatest.{OptionValues, Matchers, WordSpec}

class TopicSpec extends WordSpec with Matchers with OptionValues {
  def uglyTopic = Topic(`name` = "test!!.|~ ", `type` = "`86;'';" )
  def prettyTopic = Topic(name = "blah/etc-123-stuff", `type` = "football")
  "topic" must {
    "encode and decode an ugly topic" in {
      val topicUri = uglyTopic.toUri
      info(s"Topic $uglyTopic has URI: $topicUri")
      Topic.fromUri(topicUri).value shouldBe uglyTopic
    }
    "encode and decode a pretty topic" in {
      val topicUri = prettyTopic.toUri
      info(s"Topic $prettyTopic has URI: $topicUri")
      topicUri should fullyMatch regex """[0-9a-zA-Z\/:-=]+"""
      Topic.fromUri(topicUri).value shouldBe prettyTopic
    }
  }
}
