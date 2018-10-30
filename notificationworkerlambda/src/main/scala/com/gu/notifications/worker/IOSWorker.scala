package com.gu.notifications.worker

import java.io.{InputStream, OutputStream}

import apnsworker.Apns
import apnsworker.ApnsClient.ApnsResponse
import cats.effect.{IO, Resource}
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.amazonaws.util.IOUtils
import fs2.Pipe
import models.ShardedNotification
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsError, JsSuccess, Json}

case class Env(app: String, stack: String, stage: String) {
  override def toString: String = s"App: $app, Stack: $stack, Stage: $stage\n"
}

object Env {
  def apply(): Env = Env(
    Option(System.getenv("App")).getOrElse("DEV"),
    Option(System.getenv("Stack")).getOrElse("DEV"),
    Option(System.getenv("Stage")).getOrElse("DEV")
  )
}

object IOSWorker extends RequestStreamHandler {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val apns: Apns[IO] = ???
  def report: Pipe[IO, Either[Throwable, String], Unit] = ???

  override def handleRequest(inputStream: InputStream, output: OutputStream, context: Context): Unit = {

    def parseShardedNotification(input: String): IO[ShardedNotification] = {
      Json.parse(input).validate[ShardedNotification] match {
        case JsSuccess(shard, _) => IO.pure(shard)
        case JsError(errors) => IO.raiseError(new RuntimeException(s"Unable to parse message $errors"))
      }
    }

    val notification = for {
      input <- Resource.fromAutoCloseable(IO(inputStream)).use(stream => IO(IOUtils.toString(stream)))
      shardedNotification <- parseShardedNotification(input)
    } yield shardedNotification

    val prog: fs2.Stream[IO, ApnsResponse] = for {
      n <- fs2.Stream.eval(notification)
      res <- apns.send(n.notification, n.range)
    } yield res


    prog
      .through(report)
      .compile
      .drain
      .unsafeRunSync()

  }
}

