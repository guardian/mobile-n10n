package models

import org.apache.commons.codec.digest.DigestUtils.md5Hex
import play.api.libs.json._
import cats.syntax.all._


case class Topic(`type`: TopicType, name: String, title: Option[String] = None) {
  override def toString: String = fullName
  def toFirebaseString: String = toString.replaceAll("/", "%")
  lazy val id: String = md5Hex(toString)
  def fullName: String = s"${`type`}/$name"
}

object Topic {

  implicit val jf: OFormat[Topic] = Json.format[Topic]

  def fromString(s: String): Either[String, Topic] = {
    val formattedTopicString = s.replaceAll("%", "/")
    val (topicType, topicName) = formattedTopicString.splitAt(formattedTopicString.indexOf("/"))
    for {
      tt <- Either.fromOption(TopicType.fromString(topicType), s"Invalid topic type $topicType")
      tn = topicName.drop(1)
    } yield Topic(tt, tn)
  }

  def fromStringsIgnoringErrors(strings: List[String]): List[Topic] = {
    strings.map(fromString).collect { case Right(topic) => topic}
  }
}
