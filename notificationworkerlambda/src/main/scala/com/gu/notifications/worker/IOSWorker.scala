package com.gu.notifications.worker

import java.io.{InputStream, OutputStream}

import apnsworker.Apns
import apnsworker.ApnsClient.ApnsResponse
import cats.effect.{ContextShift, IO}
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import models.SendingResults
import _root_.models.iOS
import com.gu.notifications.worker.utils.{Cloudwatch, Logging, NotificationParser}
import db.{DatabaseConfig, RegistrationService}
import fs2.{Pipe, Stream}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext

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

class IOSWorker extends RequestStreamHandler with Logging {

  val env = Env()
  implicit val ec = ExecutionContext.Implicits.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val config: ApnsWorkerConfiguration = Configuration.fetchApns()
  val transactor = DatabaseConfig.transactor[IO](config.jdbcConfig)
  val registrationService = RegistrationService(transactor)
  val apns = new Apns(registrationService, config.apnsConfig)

  def report(prefix: String): Pipe[IO, ApnsResponse, ApnsResponse] =
    _.evalMap { resp =>
      IO.delay {
        resp match {
          case Left(e) => logger.error(s"$prefix $e") //TODO: send invalid token to queue
          case Right(_) => () // doing nothing when success
        }
        resp
      }
    }

  override def handleRequest(inputStream: InputStream, output: OutputStream, context: Context): Unit = {

    val notification = NotificationParser.fromInputStream(inputStream)

    val prog: Stream[IO, ApnsResponse] = for {
      n <- Stream.eval(notification)
      _ = logger.info(s"Sending notification ${n.notification.id}...")
      resp <- apns.send(n.notification, n.range)
    } yield resp

    prog
      .through(report("APNS failure: "))
      .fold(SendingResults.empty){ case (acc, resp) => SendingResults.inc(acc, resp) }
      .through(logInfo(prefix = s"Results "))
      .through(Cloudwatch.sendMetrics(env.stage, iOS))
      .compile
      .drain
      .unsafeRunSync()
  }
}

