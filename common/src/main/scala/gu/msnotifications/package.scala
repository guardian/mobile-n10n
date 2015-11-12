package gu

import models.{Topic, UserId}

package object msnotifications {

  private def encodeBase16(string: String): String =
    string.getBytes("UTF-8").map("%02X".format(_)).mkString.toLowerCase

  private def decodeBase16(string: String): String = {
    val bytes = string.grouped(2).map(unit => java.lang.Byte.parseByte(unit, 16))
    new String(bytes.toArray, "UTF-8")
  }

  case class Tag(encodedTag: String)

  object Tag {
    import Tags._

    def fromTopic(t: Topic): Tag = {
      Tag(s"${TopicTagPrefix}Base16:${encodeBase16(t.toString)}")
    }

    def fromUserId(u: UserId): Tag = {
      Tag(s"$UserTagPrefix${u.userId}")
    }

  }

  case class Tags(tags: Set[Tag] = Set.empty) {
    import Tags._

    def asSet = tags.map(_.encodedTag)

    def findUserId: Option[UserId] = encodedTags
      .find(_.matches(UserTagRegex.regex))
      .map { case UserTagRegex(uname) => UserId(uname) }

    def decodedTopics: Set[Topic] = {
      val topics = encodedTags.collect{ case TopicTagRegex(encodedTopic) => decodeBase16(encodedTopic) }
      topics.map(Topic.fromString).flatMap(_.toOption)
    }

    def withUserId(userId: UserId) = copy(tags + Tag.fromUserId(userId))

    def withTopics(topics: Set[Topic]) = copy(tags ++ topics.map(Tag.fromTopic))

    private[this] def encodedTags = tags.map(_.encodedTag)
  }

  object Tags {
    val UserTagPrefix = "user:"
    val TopicTagPrefix = "topic:"
    val UserTagRegex = """user:(.*)""".r
    val TopicTagRegex = """topic:Base16:(.*)""".r

    def fromStrings(tags: Set[String]) = Tags(tags.map(Tag(_)))
  }
}