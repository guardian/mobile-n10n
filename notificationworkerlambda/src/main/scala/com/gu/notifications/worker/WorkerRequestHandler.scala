package com.gu.notifications.worker

import _root_.models.{Newsstand, Topic, TopicTypes}
import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.worker.delivery._
import models.SendingResults
import com.gu.notifications.worker.utils.{Cloudwatch, Logging, NotificationParser, Reporting}
import org.slf4j.{Logger, LoggerFactory}
import fs2.{Chunk, Sink, Stream}
import _root_.models.{Platform, ShardedNotification}
import com.gu.notifications.worker.cleaning.CleaningClient
import com.gu.notifications.worker.delivery.DeliveryException.InvalidToken
import com.gu.notifications.worker.tokens.{ChunkedTokens, TokenService}

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
  def tokenService: IO[TokenService[IO]]
  val cleaningClient: CleaningClient
  val cloudwatch: Cloudwatch
  def maxConcurrency: Int

  def env = Env()
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def handleRequest(event: SQSEvent, context: Context): Unit = {
    def eitherShardOrChunks: Stream[IO, Either[ShardedNotification, ChunkedTokens]] = Stream.eval(
      for {
        json <- event.getRecords.asScala.headOption.map(r => IO(r.getBody)).getOrElse(IO.raiseError(new RuntimeException("SQSEvent has no element")))
        n <- NotificationParser.parseEventNotification(json)
      } yield n
    )

    def reportSuccesses[C <: DeliveryClient](eitherShardOrChunks: Either[ShardedNotification, ChunkedTokens]): Sink[IO, Either[DeliveryException, DeliverySuccess]] = { input =>
      val (notificationId, range) = eitherShardOrChunks match {
        case Left(shardedNotification) => (shardedNotification.notification.id, shardedNotification.range.toString)
        case Right(chunkedTokens) => (chunkedTokens.notification.id, chunkedTokens.tokens.size.toString)
      }
      val notificationLog = s"(notification: ${notificationId} ${range})"
      input.fold(SendingResults.empty){ case (acc, resp) => SendingResults.aggregate(acc, resp) }
        .evalTap(logInfo(prefix = s"Results $notificationLog: "))
        .to(cloudwatch.sendMetrics(env.stage, platform))
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
      eitherNC <- eitherShardOrChunks
      deliveryService <- Stream.eval(deliveryService)
      tokenService <- Stream.eval(tokenService)
      chunkedTokens <- eitherNC match {
        case Left(n) => {
          val notificationLog = s"(notification: ${n.notification.id} ${n.range})"
          logger.info(s"Sending notification $notificationLog...")
          tokenService.batchTokens(n.notification, n.range, n.platform.getOrElse(platformFromTopics(n.notification.topic)))
        }
        case Right(chunkedTokens) => Stream.eval(IO.pure(chunkedTokens))
      }
      individualNotifications = Stream.evalUnChunk(IO.pure(Chunk.seq(chunkedTokens.toNotificationToSends)))
      resp <- individualNotifications
        .map(token => deliveryService.send(token.notification, token.token, token.platform))
        .parJoin(maxConcurrency)
        .evalTap(Reporting.log(s"Sending failure: "))
        .broadcastTo(reportSuccesses(eitherNC), cleanupFailures)
    } yield resp

    prog
      .compile
      .drain
      .unsafeRunSync()
  }
}
