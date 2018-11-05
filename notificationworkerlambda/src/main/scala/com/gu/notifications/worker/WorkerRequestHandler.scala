package com.gu.notifications.worker

import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.worker.delivery._
import models.SendingResults
import com.gu.notifications.worker.utils.{Cloudwatch, Logging, NotificationParser, Reporting}
import org.slf4j.{Logger, LoggerFactory}
import fs2.Stream
import _root_.models.{Platform, ShardedNotification}

import collection.JavaConverters._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

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

trait WorkerRequestHandler[C <: DeliveryClient] extends RequestHandler[SQSEvent, Unit] with Logging {

  def platform: Platform
  def deliveryService: IO[DeliveryService[IO, C]]

  def env = Env()
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def handleRequest(event: SQSEvent, context: Context): Unit = {

    def sharedNotification: Stream[IO, ShardedNotification] = Stream.eval(
      for {
        json <- event.getRecords.asScala.headOption.map(r => IO(r.getBody)).getOrElse(IO.raiseError(new RuntimeException("SQSEvent has no element")))
        n <- NotificationParser.parseShardedNotification(json)
      } yield n
    )

    val prog: Stream[IO, Unit] = for {
      deliveryService <- Stream.eval(deliveryService)
      n <- sharedNotification
      notificationLog = s"(notification: ${n.notification.id} ${n.range})"
      _ = logger.info(s"Sending notification $notificationLog...")
      resp <- deliveryService.send(n.notification, n.range)
        .through(Reporting.report(s"Sending failure: "))
        .fold(SendingResults.empty){ case (acc, resp) => SendingResults.inc(acc, resp) }
        .through(logInfo(prefix = s"Results $notificationLog: "))
        .through(Cloudwatch.sendMetrics(env.stage, platform))
    } yield resp

    prog
      .compile
      .drain
      .unsafeRunSync()
  }
}
