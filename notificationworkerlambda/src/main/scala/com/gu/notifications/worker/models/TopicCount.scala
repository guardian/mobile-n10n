package com.gu.notifications.worker.models

import play.api.libs.json.{Format, Json}

case class TopicCount(topicName: String,  registrationCount: Long)

object TopicCount {
  implicit val topicCountJf: Format[TopicCount] = Json.format[TopicCount]
}