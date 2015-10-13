package gu.msnotifications

import models.{UserId, Topic}
import models.TopicTypes.{TagBlog, FootballMatch}
import org.specs2.mutable.Specification

class TagSpec extends Specification {

  "Hub Tag" should {

    "encode and decode an ugly topic" in {
      val uglyTopic = Topic(name = "test!!.|~ ", `type` = FootballMatch)
      val tag = Tag.fromTopic(uglyTopic)

      Tag(tag.encodedUri) must beEqualTo(tag)
    }

    "encode and decode a pretty topic" in {
      def prettyTopic = Topic(name = "blah/etc-123-stuff", `type` = FootballMatch)
      val tag = Tag.fromTopic(prettyTopic)

      tag.encodedUri must be matching """[0-9a-zA-Z\/:-=]+"""
      Tag(tag.encodedUri) must beEqualTo(tag)
    }

    "encode tags and return decoded topics" in {
      val topics = Set(
        Topic(`type` = FootballMatch, name = "some match"),
        Topic(`type` = TagBlog, name = "blogger")
      )

      val tagsWithTopicsAndUser = Tags().withUserId(UserId("userA")).withTopics(topics)

      tagsWithTopicsAndUser.decodedTopics must beEqualTo(topics)
    }
  }
}
