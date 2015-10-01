package models

import gu.msnotifications.WNSTopic
import models.TopicTypes.FootballMatch
import org.specs2.mutable.Specification

class TopicSpec extends Specification {

  "topic" should {

    "encode and decode an ugly topic" in {
      val uglyTopic = Topic(name = "test!!.|~ ", `type` = FootballMatch)

      val topic = WNSTopic.fromTopic(uglyTopic)

      WNSTopic.fromUri(topic.uri) must beSome(uglyTopic)
    }
    "encode and decode a pretty topic" in {
      def prettyTopic = Topic(name = "blah/etc-123-stuff", `type` = FootballMatch)
      val topicUri = WNSTopic.fromTopic(prettyTopic).uri

      topicUri must be matching """[0-9a-zA-Z\/:-=]+"""
      WNSTopic.fromUri(topicUri) must beSome(prettyTopic)
    }
  }
}
