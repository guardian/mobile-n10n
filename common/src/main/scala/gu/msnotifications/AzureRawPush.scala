package gu.msnotifications

import models.{UserId, Push, Topic}
import play.libs.Json

case class AzureRawPush(wnsType: String, body: String, tags: Option[Set[Tag]]) {
  def tagQuery: Option[String] = tags.map { set =>
    set.map(_.encodedTag).mkString("(", " && ", ")")
  }
}

object AzureRawPush {
  def fromPush(push: Push) = {
    val body = Json.stringify(Json.toJson(push.notification.payload))
    push.destination match {
      case Left(topic: Topic) => AzureRawPush("wns/raw", body, Some(Set(Tag.fromTopic(topic))))
      case Right(user: UserId) => AzureRawPush("wns/raw", body, Some(Set(Tag.fromUserId(user))))
    }
  }
}