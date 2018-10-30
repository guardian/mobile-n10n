package com.gu.notifications.worker.utils

import java.io.InputStream

import cats.effect.{IO, Resource}
import com.amazonaws.util.IOUtils
import models.ShardedNotification
import play.api.libs.json.{JsError, JsSuccess, Json}

object NotificationParser {

  private def parseShardedNotification(input: String): IO[ShardedNotification] = {
    Json.parse(input).validate[ShardedNotification] match {
      case JsSuccess(shard, _) => IO.delay(shard)
      case JsError(errors) => IO.raiseError(new RuntimeException(s"Unable to parse message $errors"))
    }
  }

  def fromInputStream(inputStream: InputStream): IO[ShardedNotification] = for {
    input <- Resource.fromAutoCloseable(IO(inputStream)).use(is => IO(IOUtils.toString(is)))
    shardedNotification <- parseShardedNotification(input)
  } yield shardedNotification

}
