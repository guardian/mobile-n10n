package com.gu.notifications.worker

import java.util.UUID

import _root_.models.{Newsstand, Platform, ShardRange, ShardedNotification, Topic, TopicTypes}
import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.gu.notifications.worker.cleaning.CleaningClient
import com.gu.notifications.worker.delivery.DeliveryException.InvalidToken
import com.gu.notifications.worker.delivery._
import com.gu.notifications.worker.models.SendingResults
import com.gu.notifications.worker.tokens.{ChunkedTokens, IndividualNotification, SqsDeliveryService, TokenService}
import com.gu.notifications.worker.utils.{Cloudwatch, Logging, NotificationParser, Reporting}
import fs2.{Pipe, Sink, Stream}
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
    def eitherShardNotificationsOrChunkedTokensStream: Stream[IO, Either[ShardedNotification, ChunkedTokens]] = Stream.emits(event.getRecords.asScala)
      .map(r => r.getBody)
      .map(NotificationParser.parseEventNotification)

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

    def deliverIndividualNotificationStream(individualNotificationStream: Stream[IO, IndividualNotification]): Stream[IO, Either[DeliveryException, C#Success]] = for {
      deliveryService <- Stream.eval(deliveryService)
      resp <- individualNotificationStream.map(individualNotification => deliveryService.send(individualNotification.notification, individualNotification.token, individualNotification.platform))
        .parJoin(maxConcurrency)
        .evalTap(Reporting.log(s"Sending failure: "))
    } yield resp


    def deliverShardedNotification(shardNotifications: ShardedNotification): Stream[IO, Unit] = for {
      tokenService <- Stream.eval(tokenService)
      notificationLog = s"(notification: ${shardNotifications.notification.id} ${shardNotifications.range})"
      _ = logger.info(s"Sending notification $notificationLog...")
      platform = shardNotifications.platform.getOrElse(platformFromTopics(shardNotifications.notification.topic))
      tokenStream = tokenService.tokens(shardNotifications.notification, shardNotifications.range, platform)
      individualNotificationStream = tokenStream.map(IndividualNotification(shardNotifications.notification, _, platform))
      resultStream = deliverIndividualNotificationStream(individualNotificationStream)
      resultSink = reportSuccesses(shardNotifications.notification.id, shardNotifications.range)
      resp <- resultStream.broadcastTo(resultSink, cleanupFailures)
    } yield resp

    val sinkLogErrorResults: Sink[IO, Either[Throwable, Any]] = results =>
      for {
        throwable <- results.collect {
          case Left(throwableFound) => throwableFound
        }
        _ = logger.warn("Error queueing", throwable)
      } yield ()

    def queueShardedNotification(shardNotifications: Stream[IO, ShardedNotification]): Stream[IO, Unit] = for {
        sqsDeliveryService <- Stream.eval(sqsDeliveryService)
        tokenService <- Stream.eval(tokenService)
        chunkedTokens = for {
          n <- shardNotifications
          notificationLog = s"(notification: ${n.notification.id} ${n.range})"
          _ = logger.info(s"Queuing notification $notificationLog...")
          tokens <- tokenService.tokens(n.notification, n.range, platform).chunkN(1000)
        } yield ChunkedTokens(n.notification, tokens.toList, platform, n.range)
        resp <- chunkedTokens.chunkN(10)
          .map(_.toList)
          .map(sqsDeliveryService.sending)
          .parJoin(maxConcurrency)
          .to(sinkLogErrorResults)
      } yield resp


    def shouldDeliverToSqs(shardedNotification: ShardedNotification): Boolean = false

    val pipeShardNotificationToDeliveries: Pipe[IO, Either[ShardedNotification, ChunkedTokens], Unit] = eitherNC => {
      for {
        shardedNotification <- eitherNC.collect {
          case Left(shardedNotificationFound) if !shouldDeliverToSqs(shardedNotificationFound) => shardedNotificationFound
        }
        resp <- deliverShardedNotification(shardedNotification)
      } yield resp
    }

    val pipeSharedNotificationsToQueue: Pipe[IO, Either[ShardedNotification, ChunkedTokens], Unit] = eitherNC => {
      val shardNotifications = eitherNC.collect {
        case Left(shardedNotification) if shouldDeliverToSqs(shardedNotification) => shardedNotification
      }
      queueShardedNotification(shardNotifications)
    }

    val pipeChunkedTokenToDeliveries: Pipe[IO, Either[ShardedNotification, ChunkedTokens], Unit] = eitherNC => {
      for {
        chunkedTokens <- eitherNC.collect {
          case Right(chunkedTokensFound) => chunkedTokensFound
        }
        individualNotifications = Stream.emits(chunkedTokens.toNotificationToSends).covary[IO]
        resp <- deliverIndividualNotificationStream(individualNotifications)
          .broadcastTo(reportSuccesses(chunkedTokens.notification.id, chunkedTokens.range), cleanupFailures)
      } yield resp
    }

    val prog: Stream[IO, Unit] = eitherShardNotificationsOrChunkedTokensStream
      .broadcastThrough(pipeShardNotificationToDeliveries, pipeSharedNotificationsToQueue, pipeChunkedTokenToDeliveries)
    prog
      .compile
      .drain
      .unsafeRunSync()
  }
}
