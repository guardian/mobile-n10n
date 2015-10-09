package gu.msnotifications

import models.Topic
import models.TopicTypes.FootballMatch
import org.specs2.mutable.Specification

class WNSTagSpec extends Specification {

  "topic" should {

    "encode and decode an ugly topic" in {
      val uglyTopic = Topic(name = "test!!.|~ ", `type` = FootballMatch)
      val tag = WNSTag.fromTopic(uglyTopic)

      WNSTag(tag.encodedUri) must beEqualTo(tag)
    }

    "encode and decode a pretty topic" in {
      def prettyTopic = Topic(name = "blah/etc-123-stuff", `type` = FootballMatch)
      val tag = WNSTag.fromTopic(prettyTopic)

      tag.encodedUri must be matching """[0-9a-zA-Z\/:-=]+"""
      WNSTag(tag.encodedUri) must beEqualTo(tag)
    }
  }
}
