package azure

import java.util.UUID

import models.{UniqueDeviceIdentifier, Topic}
import models.TopicTypes.{Content, TagContributor, FootballMatch}
import org.specs2.mutable.Specification

class TagSpec extends Specification {

  "Azure hub tag" should {

    "encode an ugly topic" in {
      val uglyTopic = Topic(name = "test!!.|~ ", `type` = FootballMatch)
      val tag = Tag.fromTopic(uglyTopic)

      Tag(tag.encodedTag) must beEqualTo(tag)
    }

    "encode a pretty topic" in {
      val prettyTopic = Topic(name = "blah/etc-123-stuff", `type` = FootballMatch)
      val tag = Tag.fromTopic(prettyTopic)

      tag.encodedTag must be matching """[0-9a-zA-Z\/:-=]+"""
      Tag(tag.encodedTag) must beEqualTo(tag)
    }

    "encoded tag must end with topic id" in {
      val topic = Topic(name = "profile/josh-halliday", `type` = TagContributor)
      val tag = Tag.fromTopic(topic)

      tag.encodedTag must endWith(topic.id)
    }

    "encoded tag must not exceed 120 characters for long topic names" in {
      val topic = Topic(name = "uk-news/live/2016/apr/26/hillsborough-disaster-inquest-jury-returns-verdict-live-updates", `type` = Content)
      val tag = Tag.fromTopic(topic)

      tag.encodedTag must haveLength(beLessThan(120))
    }

    "encoded tag must contain user id" in {
      val uuid = UUID.randomUUID
      val userId = UniqueDeviceIdentifier(uuid)
      val tag = Tag.fromUserId(userId)

      tag.encodedTag must endWith(uuid.toString)
    }

    "encoded tag should not be influenced by 'gia:' prefix" in {
      val uuid = UUID.randomUUID
      val userId = UniqueDeviceIdentifier(uuid)
      val userIdWithPrefix = UniqueDeviceIdentifier(uuid, Some("gia:"))
      Tag.fromUserId(userId) must beEqualTo(Tag.fromUserId(userIdWithPrefix))
    }

    "encoded tag should not be influenced by uuid case" in {
      val uuid = UUID.randomUUID
      val userIdUppercase = UniqueDeviceIdentifier(uuid, uppercaseUuid = true)
      val userIdLowercase = UniqueDeviceIdentifier(uuid, uppercaseUuid = false)
      Tag.fromUserId(userIdUppercase) must beEqualTo(Tag.fromUserId(userIdLowercase))
    }
  }
}
