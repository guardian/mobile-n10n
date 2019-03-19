package models

import play.api.libs.json.{Format, Json}

case class TopicCount(topicName: String,  registrationCount: Long)

object TopicCount{
  implicit val topicCountJF: Format[TopicCount] = Json.format[TopicCount]
}