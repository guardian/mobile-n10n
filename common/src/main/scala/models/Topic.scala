package models

import org.apache.commons.codec.digest.DigestUtils.md5Hex
import play.api.libs.json._

import cats.syntax.either._

case class Topic(`type`: TopicType, name: String) {
  override def toString: String = s"${`type`}/$name"
  lazy val id: String = md5Hex(toString)
}

object Topic {

  implicit val jf = Json.format[Topic]

  def fromString(s: String): Either[String, Topic] = {
    val (topicType, topicName) = s.splitAt(s.indexOf("/"))
    for {
      tt <- Either.fromOption(TopicType.fromString(topicType), s"Invalid topic type $topicType")
      tn = topicName.drop(1)
    } yield Topic(tt, tn)
  }
}
