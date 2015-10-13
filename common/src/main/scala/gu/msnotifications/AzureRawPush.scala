package gu.msnotifications

import models.{UserId, Push, Topic}
import play.libs.Json

case class AzureRawPush(wnsType: String, body: String, tags: Option[Set[WNSTag]]) {
  def tagQuery: Option[String] = tags.map { set =>
    set.map(_.encodedUri).mkString("(", " && ", ")")
  }
}

object AzureRawPush {
  def fromPush(push: Push): AzureRawPush = {
    val body = Json.stringify(Json.toJson(push.notification.payload))
    push.destination match {
      case Left(topic: Topic) => AzureRawPush("wns/raw", body, Some(Set(WNSTag.fromTopic(topic))))
      case Right(user: UserId) => AzureRawPush("wns/raw", body, Some(Set(WNSTag.fromUserId(user))))
    }
  }
}
