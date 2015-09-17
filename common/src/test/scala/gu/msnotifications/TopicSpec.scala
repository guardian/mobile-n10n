package gu.msnotifications

import org.scalatest.{OptionValues, Matchers, WordSpec}
import models.Topic
import models.TopicTypes.FootballMatch

class TopicSpec extends WordSpec with Matchers with OptionValues {
  def uglyTopic = Topic(`name` = "test!!.|~ ", `type` = FootballMatch )
  def prettyTopic = Topic(name = "blah/etc-123-stuff", `type` = FootballMatch)
  "topic" must {
    "encode and decode an ugly topic" in {
      val topicUri = uglyTopic.toWNSUri
      info(s"Topic $uglyTopic has URI: $topicUri")
      WNSTopic.fromUri(topicUri).value shouldBe uglyTopic
    }
    "encode and decode a pretty topic" in {
      val topicUri = prettyTopic.toWNSUri
      info(s"Topic $prettyTopic has URI: $topicUri")
      topicUri should fullyMatch regex """[0-9a-zA-Z\/:-=]+"""
      WNSTopic.fromUri(topicUri).value shouldBe prettyTopic
    }
  }
}
