package models

import play.api.libs.json.{Format, Json}

case class TopicCount(topicName: String,  registrationCount: Int)

object TopicCount{
  implicit val topicCountJF: Format[TopicCount] = Json.format[TopicCount]
}