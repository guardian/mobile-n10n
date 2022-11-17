package com.gu.notifications.worker.utils

import com.gu.notifications.worker.tokens.ChunkedTokens
import models.{ShardRange, ShardedNotification}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json._

case class Event(
  range: Option[ShardRange],
  tokens: Option[List[String]]
)

object Event {
  implicit val eventJf: Format[Event] = Json.format[Event]
}

object NotificationParser extends Logging {
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)
  def parseEventNotification(input: String): Either[ShardedNotification, ChunkedTokens] = {
    val json: JsValue = Json.parse(input)
    json.validate[Event] match {
      case JsSuccess(value, _) => {
        value.tokens.map(_ => Right(parseChunkedTokens(json))).getOrElse(value.range.map(_ => Left(parseShardedNotification(json))).getOrElse(throw new RuntimeException(s"Unable to parse message: not chunks or shard")))
      }
      case JsError(errors) => throw new RuntimeException(s"Unable to parse message $errors")
    }

  }

  def parseChunkedTokenEvent(input: String): ChunkedTokens = {
    val json: JsValue = Json.parse(input)
    parseChunkedTokens(json)
  }

  def parseShardNotificationEvent(input: String): ShardedNotification = {
    val json: JsValue = Json.parse(input)
    parseShardedNotification(json)
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
