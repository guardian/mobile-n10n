package com.gu.notifications.worker

import _root_.models.{Android, Newsstand, ShardedNotification, iOS}
import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.worker.tokens.{ChunkedTokens, SqsDeliveryServiceImpl, TokenServiceImpl}
import com.gu.notifications.worker.utils.{Logging, NotificationParser}
import db.{DatabaseConfig, RegistrationService}
import doobie.util.transactor.Transactor
import fs2.{Sink, Stream}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


class Harvester extends Logging {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val config: HarvesterConfiguration = Configuration.fetchHarvester()
  val transactor: Transactor[IO] = DatabaseConfig.transactor[IO](config.jdbcConfig)
  val registrationService = RegistrationService(transactor)
  val tokenServiceImpl = new TokenServiceImpl[IO](registrationService)

  val apnsDeliveryService = new SqsDeliveryServiceImpl[IO](config.apnsSqsUrl)

  val firebaseDeliveryService = new SqsDeliveryServiceImpl[IO](config.firebaseSqsUrl)

  val maxConcurrency: Int = 100

  def env = Env()



  val sinkLogErrorResults: Sink[IO, Either[Throwable, Any]] = results =>
    for {
      throwable <- results.collect {
        case Left(throwableFound) => throwableFound
      }
      _ = logger.warn("Error queueing", throwable)
    } yield ()


  val apnsSink: Sink[IO, ChunkedTokens] = chunkedTokens => {
    chunkedTokens.filter(chunkedTokens => chunkedTokens.platform == iOS || chunkedTokens.platform == Newsstand)
      .map(firebaseDeliveryService.sending)
      .parJoin(maxConcurrency)
      .to(sinkLogErrorResults)
  }

  val firebaseSink: Sink[IO, ChunkedTokens] = chunkedTokens => {
    chunkedTokens.filter(_.platform == Android)
      .map(firebaseDeliveryService.sending)
      .parJoin(maxConcurrency)
      .to(sinkLogErrorResults)
  }

  def queueShardedNotification(shardNotifications: Stream[IO, ShardedNotification]): Stream[IO, Unit] = {
    val chunkedTokenStream = for {
      n <- shardNotifications
      platform = n.platform
      notificationLog = s"(notification: ${n.notification.id} ${n.range})"
      _ = logger.info(s"Queuing notification $notificationLog...")
      tokens <- tokenServiceImpl.tokens(n.notification, n.range, platform).chunkN(1000)
    } yield ChunkedTokens(n.notification, tokens.toList, platform, n.range)
    chunkedTokenStream.broadcastTo(firebaseSink, apnsSink)
  }

  def handleShardedNotification(event: SQSEvent, context: Context): Unit = {
    val shardNotificationStream: Stream[IO, ShardedNotification] = Stream.emits(event.getRecords.asScala)
      .map(r => r.getBody)
      .map(NotificationParser.parseShardNotificationEvent)
    queueShardedNotification(shardNotificationStream)
      .compile
      .drain
      .unsafeRunSync()
  }

  def handleRequest(event: SQSEvent, context: Context): Unit = handleShardedNotification(event, context)

}
