package com.gu.notifications.worker

import _root_.models.{Android, Newsstand, Notification, Platform, ShardRange, ShardedNotification, iOS}
import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.worker.tokens.{ChunkedTokens, SqsDeliveryService, SqsDeliveryServiceImpl, TokenService, TokenServiceImpl}
import com.gu.notifications.worker.utils.{Cloudwatch, CloudwatchImpl, Logging, NotificationParser}
import db.{DatabaseConfig, RegistrationService}
import doobie.util.transactor.Transactor
import fs2.{Sink, Stream}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


trait HarvesterRequestHandler extends Logging {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val env = Env()
  val apnsDeliveryService: SqsDeliveryService[IO]
  val firebaseDeliveryService: SqsDeliveryService[IO]
  val tokenService: TokenService[IO]
  val cloudwatch: Cloudwatch
  val maxConcurrency: Int = 100
  val logErrors: Sink[IO, Throwable] = throwables => {
    throwables.map(throwable => logger.warn("Error queueing", throwable))
  }

  def sinkErrors(platform: Platform): Sink[IO, Throwable] = throwables => {
    throwables.broadcastTo(logErrors, cloudwatch.sendFailures(env.stage, platform))
  }

  def firebaseSinkBuilder(notification: Notification, range: ShardRange): Sink[IO, (String, Platform)] = {
    val androidSinkErrors = sinkErrors(Android)
    tokens =>
      tokens
        .collect {
          case (token, Android) => token
        }
        .chunkN(1000)
        .map(chunk => ChunkedTokens(notification, chunk.toList, Android, range))
        .map(firebaseDeliveryService.sending)
        .parJoin(maxConcurrency)
        .collect {
          case Left(throwable) => throwable
        }
        .to(androidSinkErrors)
  }

  def apnsSink(notification: Notification, range: ShardRange, platform: Platform): Sink[IO, (String, Platform)] = {
    val iosSinkErrors = sinkErrors(iOS)
    tokens =>
      tokens
        .collect {
          case (token, tokenPlatform) if tokenPlatform == platform => token
        }
        .chunkN(1000)
        .map(chunk => ChunkedTokens(notification, chunk.toList, platform, range))
        .map(apnsDeliveryService.sending)
        .parJoin(maxConcurrency)
        .collect {
          case Left(throwable) => throwable
        }
        .to(iosSinkErrors)

  }

  def queueShardedNotification(shardNotifications: Stream[IO, ShardedNotification]): Stream[IO, Unit] = {
    for {
      n <- shardNotifications
      firebaseSink = firebaseSinkBuilder(n.notification, n.range)
      newsstandSink = apnsSink(n.notification, n.range, Newsstand)
      iosSink = apnsSink(n.notification, n.range, iOS)
      notificationLog = s"(notification: ${n.notification.id} ${n.range})"
      _ = logger.info(s"Queuing notification $notificationLog...")
      tokens = n.platform match {
          case Some(platform) => tokenService.tokens(n.notification, n.range, platform).map(token => (token, platform))
          case None => tokenService.tokens(n.notification, n.range)
        }
      resp <- tokens.broadcastTo(firebaseSink, newsstandSink, iosSink)
    } yield resp
  }

  def handleHarvesting(event: SQSEvent, context: Context): Unit = {
    val shardNotificationStream: Stream[IO, ShardedNotification] = Stream.emits(event.getRecords.asScala)
      .map(r => r.getBody)
      .map(NotificationParser.parseShardNotificationEvent)
    queueShardedNotification(shardNotificationStream)
      .compile
      .drain
      .unsafeRunSync()
  }
}


class Harvester extends HarvesterRequestHandler {
  val config: HarvesterConfiguration = Configuration.fetchHarvester()
  val transactor: Transactor[IO] = DatabaseConfig.transactor[IO](config.jdbcConfig)
  val registrationService: RegistrationService[IO, Stream] = RegistrationService(transactor)
  override val cloudwatch: Cloudwatch = new CloudwatchImpl
  override val tokenService: TokenServiceImpl[IO] = new TokenServiceImpl[IO](registrationService)
  override val apnsDeliveryService: SqsDeliveryService[IO] = new SqsDeliveryServiceImpl[IO](config.apnsSqsUrl)
  override val firebaseDeliveryService: SqsDeliveryService[IO] = new SqsDeliveryServiceImpl[IO](config.firebaseSqsUrl)
}
