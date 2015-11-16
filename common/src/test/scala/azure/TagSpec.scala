package azure

import java.util.UUID

import models.{UserId, Topic}
import models.TopicTypes.{TagBlog, FootballMatch}
import org.specs2.mutable.Specification

class TagSpec extends Specification {

  "Hub Tag" should {

    "encode and decode an ugly topic" in {
      val uglyTopic = Topic(name = "test!!.|~ ", `type` = FootballMatch)
      val tag = Tag.fromTopic(uglyTopic)

      Tag(tag.encodedTag) must beEqualTo(tag)
    }

    "encode and decode a pretty topic" in {
      def prettyTopic = Topic(name = "blah/etc-123-stuff", `type` = FootballMatch)
      val tag = Tag.fromTopic(prettyTopic)

      tag.encodedTag must be matching """[0-9a-zA-Z\/:-=]+"""
      Tag(tag.encodedTag) must beEqualTo(tag)
    }

    "encode tags and return decoded topics" in {
      val topics = Set(
        Topic(`type` = FootballMatch, name = "some match"),
        Topic(`type` = TagBlog, name = "blogger")
      )

      val tagsWithTopicsAndUser = Tags()
        .withUserId(UserId(UUID.fromString("988ADFF8-8961-11E5-96E3-D0DB64696656")))
        .withTopics(topics)

      tagsWithTopicsAndUser.decodedTopics must beEqualTo(topics)
    }
  }
}
