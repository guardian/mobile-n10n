package com.gu.notifications.worker.utils

import cats.effect.IO
import com.gu.notifications.worker.tokens.ChunkedTokens
import models.{ShardRange, ShardedNotification}
import play.api.libs.json.{Format, JsError, JsSuccess, JsValue, Json}

case class Event(
  range: Option[ShardRange],
  tokens: Option[List[String]]
)

object Event {
  implicit val eventJf: Format[Event] = Json.format[Event]
}

object NotificationParser {
  def parseEventNotification(input: String): IO[Either[ShardedNotification, ChunkedTokens]] = {
    IO {
      val json: JsValue = Json.parse(input)
      json.validate[Event] match {
        case JsSuccess(value, _) => value.range.map(_ => Left(parseShardedNotification(json))).getOrElse(value.tokens.map(_ => Right(parseChunkedTokens(json))).getOrElse(throw new RuntimeException(s"Unable to parse message: not chunks or shard")))
        case JsError(errors) => throw new RuntimeException(s"Unable to parse message $errors")
      }
    }
  }

  private def parseChunkedTokens(json: JsValue): ChunkedTokens = {
    json.validate[ChunkedTokens] match {
      case JsSuccess(chunkedTokens, _) => chunkedTokens
      case JsError(errors) => throw new RuntimeException(s"Unable to parse message $errors")
    }
  }

  private def parseShardedNotification(json: JsValue): ShardedNotification = {
    json.validate[ShardedNotification] match {
      case JsSuccess(shard, _) => shard
      case JsError(errors) => throw new RuntimeException(s"Unable to parse message $errors")
    }
  }

}
