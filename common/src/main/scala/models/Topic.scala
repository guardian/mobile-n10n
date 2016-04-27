package models

import org.apache.commons.codec.digest.DigestUtils.md5Hex
import play.api.libs.json._

import scalaz.\/
import scalaz.std.option.optionSyntax._

case class Topic(`type`: TopicType, name: String) {
  override def toString: String = s"${`type`}/$name"
  lazy val id: String = md5Hex(toString)
}

object Topic {

  implicit val jf = Json.format[Topic]

  def fromString(s: String): String \/ Topic = {
    val (topicType, topicName) = s.splitAt(s.indexOf("/"))
    for {
      tt <- TopicType.fromString(topicType) \/> s"Invalid topic type $topicType"
      tn = topicName.drop(1)
    } yield Topic(tt, tn)
  }
}
