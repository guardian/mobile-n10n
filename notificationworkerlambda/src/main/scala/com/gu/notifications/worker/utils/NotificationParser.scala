package com.gu.notifications.worker.utils

import cats.effect.IO
import models.ShardedNotification
import org.slf4j.Logger
import play.api.libs.json.{JsError, JsSuccess, Json}

object NotificationParser {

  def parseShardedNotification(input: String): IO[ShardedNotification] = {
    IO(
      Json.parse(input).validate[ShardedNotification] match {
        case JsSuccess(shard, _) => shard
        case JsError(errors) => throw new RuntimeException(s"Unable to parse message $errors")
      }
    )
  }

}
