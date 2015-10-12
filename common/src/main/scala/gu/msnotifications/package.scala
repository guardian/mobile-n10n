package gu

import java.util.Base64

import models.{Topic, UserId}

package object msnotifications {

  private def encode(string: String): String =
    Base64.getUrlEncoder.encodeToString(string.getBytes("UTF-8"))

  private def decode(string: String): String =
    new String(Base64.getUrlDecoder.decode(string), "UTF-8")

  case class WNSTag(encodedUri: String)

  object WNSTag {

    def fromTopic(t: Topic): WNSTag = {
      WNSTag(s"topic:${encode(t.`type`.toString)}:${encode(t.name)}")
    }

    def fromUserId(u: UserId): WNSTag = {
      WNSTag(s"user:${u.userId}")
    }

  }

  case class Tags(tags: Set[WNSTag] = Set.empty) {
    import Tags._

    def asSet = tags.map(_.encodedUri)

    def findUserId: Option[UserId] = tags
      .map(_.encodedUri)
      .find(_.matches(UserTagRegex.regex))
      .map { case UserTagRegex(uname) => UserId(uname) }

    def withUserId(userId: UserId) = copy(tags + WNSTag.fromUserId(userId))

    def withTopics(topics: Set[Topic]) = copy(tags ++ topics.map(WNSTag.fromTopic))
  }

  object Tags {
    val UserTagPrefix = "user:"
    val UserTagRegex = """user:(.*)""".r
    val TopicTagRegex = s"""topic:(.*):(.*)""".r

    def fromUris(tags: Set[String]) = Tags(tags.map(WNSTag(_)))
  }
}