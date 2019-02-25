package com.gu.notifications.worker

import java.util.UUID

import _root_.models.{Newsstand, Platform, ShardRange, ShardedNotification, Topic, TopicTypes}
import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.gu.notifications.worker.cleaning.CleaningClient
import com.gu.notifications.worker.delivery.DeliveryException.InvalidToken
import com.gu.notifications.worker.delivery._
import models.SendingResults
import com.gu.notifications.worker.tokens.{ChunkedTokens, IndividualNotification, SqsDeliveryService, TokenService}
import com.gu.notifications.worker.utils.{Cloudwatch, Logging, NotificationParser, Reporting}
import fs2.{Sink, Stream}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
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
  def sqsDeliveryService: IO[SqsDeliveryService[IO]]
  val cleaningClient: CleaningClient
  val cloudwatch: Cloudwatch
  val maxConcurrency: Int

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

    def reportSuccesses[C <: DeliveryClient](notificationId: UUID, range: ShardRange): Sink[IO, Either[DeliveryException, DeliverySuccess]] = { input =>
      val notificationLog = s"(notification: ${notificationId} ${range})"
      input.fold(SendingResults.empty) { case (acc, resp) => SendingResults.aggregate(acc, resp) }
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

    def deliverIndividualNotificationStream(individualNotificationStream: Stream[IO, IndividualNotification]): Stream[IO, Either[DeliveryException, C#Success]] = {
      for {
        deliveryService <- Stream.eval(deliveryService)
        resp <- individualNotificationStream.map(individualNotification => deliveryService.send(individualNotification.notification, individualNotification.token, individualNotification.platform))
          .parJoin(maxConcurrency)
          .evalTap(Reporting.log(s"Sending failure: "))

      } yield resp

    }

    def deliverShardedNotification(n: ShardedNotification): Stream[IO, Unit] = for {
      tokenService <- Stream.eval(tokenService)
      notificationLog = s"(notification: ${n.notification.id} ${n.range})"
      _ = logger.info(s"Sending notification $notificationLog...")
      platform = n.platform.getOrElse(platformFromTopics(n.notification.topic))
      resp <- deliverIndividualNotificationStream(tokenService.tokens(n.notification, n.range, platform).map(IndividualNotification(n.notification, _, platform)))
        .broadcastTo(reportSuccesses(n.notification.id, n.range), cleanupFailures)
    } yield resp

    def queueShardedNotification(n: ShardedNotification): Stream[IO, Either[Throwable, Unit]] = {
      for {
        sqsDeliveryService <- Stream.eval(sqsDeliveryService)
        tokenService <- Stream.eval(tokenService)
        notificationLog = s"(notification: ${n.notification.id} ${n.range})"
        _ = logger.info(s"Queuing notification $notificationLog...")
        resp <- tokenService.tokens(n.notification, n.range, platform).chunkN(10000)
          .map(tokens => sqsDeliveryService.sending(ChunkedTokens(n.notification, tokens.toList, platform, n.range)))
          .parJoin(maxConcurrency)
      } yield resp
    }

    def deliverChunkedTokens(chunkedTokens: ChunkedTokens): Stream[IO, Unit] = {
      deliverIndividualNotificationStream(Stream.emits(chunkedTokens.toNotificationToSends).covary[IO])
        .broadcastTo(reportSuccesses(chunkedTokens.notification.id, chunkedTokens.range), cleanupFailures)

    }

    val prog: Stream[IO, Unit] = for {
      eitherNC <- eitherShardOrChunks
      resp <- eitherNC match {
        case Right(chunkedTokens) => deliverChunkedTokens(chunkedTokens)
        case Left(shardedNotification) => deliverShardedNotification(shardedNotification)
      }
    } yield resp
    prog
      .compile
      .drain
      .unsafeRunSync()
  }
}
