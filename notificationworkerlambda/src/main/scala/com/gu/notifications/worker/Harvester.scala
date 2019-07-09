package com.gu.notifications.worker

import _root_.models.{Android, Ios, Newsstand, AndroidEdition, IosEdition, Platform, ShardedNotification}
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
  val supportedPlatforms = List(Ios, Android, IosEdition, AndroidEdition)

  val logErrors: Sink[IO, Throwable] = throwables => {
    throwables.map(throwable => logger.warn("Error queueing", throwable))
  }

  def sinkErrors(platform: Platform): Sink[IO, Throwable] = throwables => {
    throwables.broadcastTo(logErrors, cloudwatch.sendFailures(env.stage, platform))
  }

  def platformSink(shardedNotification: ShardedNotification, platform: Platform, deliveryService: SqsDeliveryService[IO]): Sink[IO, (String, Platform)] = {
    val iosSinkErrors = sinkErrors(Ios)
    tokens =>
      tokens
        .collect {
          case (token, tokenPlatform) if tokenPlatform == platform => token
        }
        .chunkN(1000)
        .map(chunk => ChunkedTokens(shardedNotification.notification, chunk.toList, platform, shardedNotification.range))
        .map(deliveryService.sending)
        .parJoin(maxConcurrency)
        .collect {
          case Left(throwable) => throwable
        }
        .to(iosSinkErrors)

  }

  def queueShardedNotification(shardedNotifications: Stream[IO, ShardedNotification]): Stream[IO, Unit] = {
    for {
      shardedNotification <- shardedNotifications
      androidSink = platformSink(shardedNotification, Android, firebaseDeliveryService)
      iosSink = platformSink(shardedNotification, Ios, apnsDeliveryService)
      newsstandSink = platformSink(shardedNotification, Newsstand, apnsDeliveryService)
      androidEditionSink = platformSink(shardedNotification, AndroidEdition, firebaseDeliveryService)
      iosEditionSink = platformSink(shardedNotification, IosEdition, apnsDeliveryService)
      notificationLog = s"(notification: ${shardedNotification.notification.id} ${shardedNotification.range})"
      _ = logger.info(s"Queuing notification $notificationLog...")
      tokens = tokenService.tokens(shardedNotification.notification, shardedNotification.range)
      resp <- tokens.broadcastTo(androidSink, iosSink, newsstandSink, androidEditionSink, iosEditionSink)
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
