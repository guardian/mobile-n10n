package com.gu.notifications.worker

import cats.effect.{ContextShift, IO}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.worker.delivery._
import models.SendingResults
import com.gu.notifications.worker.utils.{Cloudwatch, Logging, NotificationParser, Reporting}
import db.{DatabaseConfig, RegistrationService}
import org.slf4j.{Logger, LoggerFactory}
import fs2.Stream
import _root_.models.{iOS, ShardedNotification}

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

trait WorkerRequestHandler[S <: DeliverySuccess] extends RequestHandler[SQSEvent, Unit] with Logging {

  def config: WorkerConfiguration
  def deliveryService: IO[DeliveryService[IO, _, S, _]]

  def env = Env()
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)
  def transactor = DatabaseConfig.transactor[IO](config.jdbcConfig)
  def registrationService = RegistrationService(transactor)

  override def handleRequest(event: SQSEvent, context: Context): Unit = {

    def sharedNotification: Stream[IO, ShardedNotification] = Stream.eval(
      for {
        json <- event.getRecords.asScala.headOption.map(r => IO(r.getBody)).getOrElse(IO.raiseError(new RuntimeException("SQSEvent has no element")))
        n <- NotificationParser.parseShardedNotification(json)
      } yield n
    )

    val prog: Stream[IO, SendingResults] = for {
      deliveryService <- Stream.eval(deliveryService)
      n <- sharedNotification
      _ = logger.info(s"Sending notification $n...")
      notificationLog = s"(notification: ${n.notification.id} ${n.range})"
      resp <- deliveryService.send(n.notification, n.range)
        .through(Reporting.report(s"APNS failure $notificationLog: "))
        .fold(SendingResults.empty){ case (acc, resp) => SendingResults.inc(acc, resp) }
        .through(logInfo(prefix = s"Results $notificationLog: "))
        .through(Cloudwatch.sendMetrics(env.stage, iOS))
    } yield resp

    prog
      .compile
      .drain
      .unsafeRunSync()
  }
}
