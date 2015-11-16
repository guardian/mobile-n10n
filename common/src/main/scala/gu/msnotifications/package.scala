package gu

import java.util.Base64

import models.{TopicType, Topic, UserId}

package object msnotifications {

  private def encode(string: String): String =
    Base64.getUrlEncoder.encodeToString(string.getBytes("UTF-8"))

  private def decode(string: String): String =
    new String(Base64.getUrlDecoder.decode(string), "UTF-8")

  case class Tag(encodedUri: String)

  object Tag {
    import Tags._

    def fromTopic(t: Topic): Tag = {
      Tag(s"$TopicTagPrefix${encode(t.`type`.toString)}:${encode(t.name)}")
    }

    def fromUserId(u: UserId): Tag = {
      Tag(s"$UserTagPrefix${u.id}")
    }

  }

  case class Tags(tags: Set[Tag] = Set.empty) {
    import Tags._

    def asSet = tags.map(_.encodedUri)

    def findUserId: Option[UserId] = urisInTags
      .find(_.matches(UserTagRegex.regex))
      .map { case UserTagRegex(UserId(uuid)) => UserId(uuid) }

    def decodedTopics: Set[Topic] = for {
      encodedUri <- urisInTags
      if encodedUri.matches(TopicTagRegex.regex)
      TopicTagRegex(tt, name) = encodedUri
      topicType <- TopicType.fromString(decode(tt))
    } yield Topic(topicType, decode(name))

    def withUserId(userId: UserId) = copy(tags + Tag.fromUserId(userId))

    def withTopics(topics: Set[Topic]) = copy(tags ++ topics.map(Tag.fromTopic))

    private[this] def urisInTags = tags.map(_.encodedUri)
  }

  object Tags {
    val UserTagPrefix = "user:"
    val TopicTagPrefix = "topic:"
    val UserTagRegex = """user:(.*)""".r
    val TopicTagRegex = """topic:(.*):(.*)""".r

    def fromUris(tags: Set[String]) = Tags(tags.map(Tag(_)))
  }
}