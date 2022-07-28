package com.gu.notifications.workerlambda.tokens

import com.gu.notifications.workerlambda.models.{Notification, ShardRange}

import play.api.libs.json.{Format, Json}

import scala.concurrent.ExecutionContextExecutor

case class IndividualNotification(notification: Notification, token: String)

case class ChunkedTokens(notification: Notification, tokens: List[String], range: ShardRange) {
  def toNotificationToSends: List[IndividualNotification] = tokens.map(IndividualNotification(notification, _))
}

object ChunkedTokens {
  implicit val chunkTokensJf: Format[ChunkedTokens] = Json.format[ChunkedTokens]
}

