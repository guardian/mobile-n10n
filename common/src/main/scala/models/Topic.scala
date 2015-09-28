package models

import play.api.libs.json._

import scalaz.\/
import scalaz.std.option.optionSyntax._

case class Topic(`type`: TopicType, name: String) {
  override def toString = s"${`type`}/$name"
}

object Topic {

  implicit val jf = Json.format[Topic]

  def fromString(s: String): String \/ Topic = {
    val (topicType, topicName) = s.partition(_ != '/')
    for {
      tt <- TopicType.fromString(topicType) \/> s"Invalid topic type $topicType"
      tn = topicName.drop(1)
    } yield Topic(tt, tn)
  }
}
