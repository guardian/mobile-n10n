package com.gu.notifications.worker.models

import play.api.libs.json.{Format, Json}

case class InvalidTokens(
  tokens: List[String]
)

object InvalidTokens {
  implicit val invalidTokensJF: Format[InvalidTokens] = Json.format[InvalidTokens]
}
