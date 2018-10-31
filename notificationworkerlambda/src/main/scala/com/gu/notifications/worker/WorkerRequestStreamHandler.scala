package com.gu.notifications.worker

import java.io.{InputStream, OutputStream}

import cats.effect.{ContextShift, IO}
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.gu.notifications.worker.delivery._
import com.gu.notifications.worker.models.SendingResults
import com.gu.notifications.worker.utils.{Cloudwatch, Logging, NotificationParser, Reporting}
import db.{DatabaseConfig, RegistrationService}
import org.slf4j.{Logger, LoggerFactory}
import fs2.Stream
import _root_.models.iOS

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

trait WorkerRequestStreamHandler[S <: DeliverySuccess] extends RequestStreamHandler with Logging {

  def config: WorkerConfiguration
  def deliveryServiceIO: IO[DeliveryService[IO, _, S, _]]

  def env = Env()
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)
  def transactor = DatabaseConfig.transactor[IO](config.jdbcConfig)
  def registrationService = RegistrationService(transactor)

  override def handleRequest(inputStream: InputStream, output: OutputStream, context: Context): Unit = {

    val notification = NotificationParser.fromInputStream(inputStream)

    val prog: Stream[IO, Either[DeliveryException, S]] = for {
      deliveryService <- Stream.eval(deliveryServiceIO)
      n <- Stream.eval(notification)
      _ = logger.info(s"Sending notification ${n.notification.id}...")
      resp <- deliveryService.send(n.notification, n.range)
    } yield resp

    prog
      .through(Reporting.report("APNS failure: "))
      .fold(SendingResults.empty){ case (acc, resp) => SendingResults.inc(acc, resp) }
      .through(logInfo(prefix = s"Results "))
      .through(Cloudwatch.sendMetrics(env.stage, iOS))
      .compile
      .drain
      .unsafeRunSync()
  }
}
