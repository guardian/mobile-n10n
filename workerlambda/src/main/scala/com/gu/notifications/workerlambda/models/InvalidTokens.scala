package com.gu.notifications.workerlambda.models

import com.gu.notifications.workerlambda.delivery.DeliveryException.InvalidToken
import play.api.libs.json.{Format, Json}

case class InvalidTokens(
                          tokens: List[String]
                        )

object InvalidTokens {
  implicit val invalidTokensJF: Format[InvalidTokens] = Json.format[InvalidTokens]

  val empty = InvalidTokens(Nil)

  def inc(previous: InvalidTokens, result: Either[Exception, _]): InvalidTokens = result match {
    case Left(InvalidToken(_, token, _, _)) => InvalidTokens(token :: previous.tokens)
    case _ => previous
  }
}