package com.gu.notifications.worker

import _root_.models.{Android, AndroidEdition, Ios, IosEdition, Newsstand, Platform, ShardedNotification}
import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.worker.tokens.{ChunkedTokens, SqsDeliveryService, SqsDeliveryServiceImpl, TokenService, TokenServiceImpl}
import com.gu.notifications.worker.utils.{Cloudwatch, CloudwatchImpl, Logging, NotificationParser}
import db.BuildTier.BuildTier
import db.{BuildTier, DatabaseConfig, HarvestedToken, RegistrationService}
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

  val iosLiveDeliveryService: SqsDeliveryService[IO]
  val iosEditionDeliveryService: SqsDeliveryService[IO]
  val androidLiveDeliveryService: SqsDeliveryService[IO]
  val androidEditionDeliveryService: SqsDeliveryService[IO]
  val androidBetaDeliveryService: SqsDeliveryService[IO]

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

  def platformSink(shardedNotification: ShardedNotification, platform: Platform, deliveryService: SqsDeliveryService[IO], buildTier: Option[BuildTier] = None): Sink[IO, HarvestedToken] = {
    val platformSinkErrors = sinkErrors(platform)
    tokens =>
      tokens
        .collect {
          case HarvestedToken(token, tokenPlatform, tokenBuildTier) if tokenPlatform == platform && (buildTier.isEmpty || buildTier == tokenBuildTier) => token
        }
        .chunkN(1000)
        .map(chunk => ChunkedTokens(shardedNotification.notification, chunk.toList, shardedNotification.range))
        .map(deliveryService.sending)
        .parJoin(maxConcurrency)
        .collect {
          case Left(throwable) => throwable
        }
        .to(platformSinkErrors)

  }

  def queueShardedNotification(shardedNotifications: Stream[IO, ShardedNotification]): Stream[IO, Unit] = {
    for {
      shardedNotification <- shardedNotifications
      androidSink = platformSink(shardedNotification, Android, androidLiveDeliveryService)
      iosSink = platformSink(shardedNotification, Ios, iosLiveDeliveryService)
      newsstandSink = platformSink(shardedNotification, Newsstand, iosEditionDeliveryService)
      androidEditionSink = platformSink(shardedNotification, AndroidEdition, androidEditionDeliveryService)
      iosEditionSink = platformSink(shardedNotification, IosEdition, iosEditionDeliveryService)
      androidBetaSink = platformSink(shardedNotification, Android, androidBetaDeliveryService, Some(BuildTier.BETA))
      notificationLog = s"(notification: ${shardedNotification.notification.id} ${shardedNotification.range})"
      _ = logger.info(s"Queuing notification $notificationLog...")
      tokens = tokenService.tokens(shardedNotification.notification, shardedNotification.range)
      resp <- tokens.broadcastTo(androidSink, iosSink, newsstandSink, androidEditionSink, iosEditionSink, androidBetaSink)
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
  override val iosLiveDeliveryService: SqsDeliveryService[IO] = new SqsDeliveryServiceImpl[IO](config.iosLiveSqsUrl)
  override val androidLiveDeliveryService: SqsDeliveryService[IO] = new SqsDeliveryServiceImpl[IO](config.androidLiveSqsUrl)
  override val iosEditionDeliveryService: SqsDeliveryService[IO] = new SqsDeliveryServiceImpl[IO](config.iosEditionSqsUrl)
  override val androidEditionDeliveryService: SqsDeliveryService[IO] = new SqsDeliveryServiceImpl[IO](config.androidEditionSqsUrl)
  override val androidBetaDeliveryService: SqsDeliveryService[IO] = new SqsDeliveryServiceImpl[IO](config.androidBetaSqsUrl)
}
