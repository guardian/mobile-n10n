package com.gu.notifications.worker

import _root_.models.{Newsstand, Topic, TopicTypes}
import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.worker.delivery._
import models.SendingResults
import com.gu.notifications.worker.utils.{Cloudwatch, Logging, NotificationParser, Reporting}
import org.slf4j.{Logger, LoggerFactory}
import fs2.{Sink, Stream}
import _root_.models.{Platform, ShardedNotification}
import com.gu.notifications.worker.cleaning.CleaningClient
import com.gu.notifications.worker.delivery.DeliveryException.InvalidToken

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
  val cleaningClient: CleaningClient

  def env = Env()
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def handleRequest(event: SQSEvent, context: Context): Unit = {
    def shardedNotification: Stream[IO, ShardedNotification] = Stream.eval(
      for {
        json <- event.getRecords.asScala.headOption.map(r => IO(r.getBody)).getOrElse(IO.raiseError(new RuntimeException("SQSEvent has no element")))
        n <- NotificationParser.parseShardedNotification(json)
      } yield n
    )

    processShardedNotification(shardedNotification)
  }

  def processShardedNotification(shardedNotification: Stream[IO, ShardedNotification]): Unit = {

    def reportSuccesses[C <: DeliveryClient](notification: ShardedNotification): Sink[IO, Either[DeliveryException, DeliverySuccess]] = { input =>
      val notificationLog = s"(notification: ${notification.notification.id} ${notification.range})"
      input.fold(SendingResults.empty){ case (acc, resp) => SendingResults.aggregate(acc, resp) }
        .evalTap(logInfo(prefix = s"Results $notificationLog: "))
        .to(Cloudwatch.sendMetrics(env.stage, platform))
    }

    def cleanupFailures[C <: DeliveryClient]: Sink[IO, Either[DeliveryException, DeliverySuccess]] = { input =>
      input
        .collect {
          case Left(InvalidToken(_, token, _, _)) =>
            logger.debug(s"Invalid token $token")
            token
        }
        .chunkN(1000)
        .to(cleaningClient.sendInvalidTokensToCleaning)
    }

    def platformFromTopics(topics: List[Topic]): Platform = {
      if (topics.exists(_.`type` == TopicTypes.NewsstandShard)) {
        Newsstand
      } else {
        platform
      }
    }

    val prog: Stream[IO, Unit] = for {
      n <- shardedNotification
      deliveryService <- Stream.eval(deliveryService)
      notificationLog = s"(notification: ${n.notification.id} ${n.range})"
      _ = logger.info(s"Sending notification $notificationLog...")
      resp <- deliveryService.send(n.notification, n.range, platformFromTopics(n.notification.topic))
        .evalTap(Reporting.log(s"Sending failure: "))
        .broadcastTo(reportSuccesses(n), cleanupFailures)
    } yield resp

    prog
      .compile
      .drain
      .unsafeRunSync()
  }
}
