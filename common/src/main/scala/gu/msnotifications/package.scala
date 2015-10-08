package gu

import java.util.Base64
import models.{UserId, TopicType, Topic}

package object msnotifications {

  private def encode(string: String): String =
    Base64.getUrlEncoder.encodeToString(string.getBytes("UTF-8"))

  private def decode(string: String): String =
    new String(Base64.getUrlDecoder.decode(string), "UTF-8")

  case class WNSTopic(uri: String)

  object WNSTopic {
    def fromUri(uri: String): Option[Topic] = {
      val regex = s"""topic:(.*):(.*)""".r
      PartialFunction.condOpt(uri) {
        case regex(tpe, name) if TopicType.fromString(decode(tpe)).isDefined =>
          Topic(`type` = TopicType.fromString(decode(tpe)).get, name = decode(name))
      }
    }

    def fromTopic(t: Topic): WNSTopic = {
      WNSTopic(s"topic:${encode(t.`type`.toString)}:${encode(t.name)}")
    }

    def fromUserId(u: UserId): WNSTopic = {
      WNSTopic(s"user:${u.userId}")
    }
  }

  case class Tags(tags: Set[String] = Set.empty) {
    import Tags._

    def asSet = tags

    def findUserId: Option[UserId] = tags
      .find(_.startsWith(UserTagPrefix))
      .map { s => UserId(s.split(':')(1)) }

    def addUserId(userId: UserId) = copy(tags + s"${UserTagPrefix}${userId.userId}")

    def addTopics(topics: Set[Topic]) = copy(tags ++ topics.map(_.name))
  }

  object Tags {
    val UserTagPrefix = "user:"
  }
}