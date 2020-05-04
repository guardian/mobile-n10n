package com.gu.notifications.worker

import _root_.models.{Android, AndroidBeta, AndroidEdition, Ios, IosEdition, Newsstand, Platform, ShardedNotification}
import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.worker.tokens.{ChunkedTokens, SqsDeliveryService, SqsDeliveryServiceImpl, TokenService, TokenServiceImpl}
import com.gu.notifications.worker.utils.{Cloudwatch, CloudwatchImpl, Logging, NotificationParser}
import db.BuildTier.BuildTier
import db.{BuildTier, DatabaseConfig, HarvestedToken, RegistrationService}
import doobie.util.transactor.Transactor
import fs2.{Pipe, Sink, Stream}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

sealed trait WorkerSqs
object WorkerSqs {
  case object AndroidWorkerSqs extends WorkerSqs
  case object AndroidBetaWorkerSqs extends WorkerSqs
  case object AndroidEditionWorkerSqs extends WorkerSqs
  case object IosWorkerSqs extends WorkerSqs
  case object IosEditionWorkerSqs extends WorkerSqs
}

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

  val logErrors: Pipe[IO, Throwable, Unit] = throwables => {
    throwables.map(throwable => logger.warn("Error queueing", throwable))
  }

  def sinkErrors(platform: Platform): Pipe[IO, Throwable, Unit] = throwables => {
    throwables.broadcastTo(logErrors, cloudwatch.sendFailures(env.stage, platform))
  }

  def platformSink(shardedNotification: ShardedNotification, platform: Platform, workerSqs: WorkerSqs, deliveryService: SqsDeliveryService[IO]): Pipe[IO, (WorkerSqs, HarvestedToken), Unit] = {
    val platformSinkErrors = sinkErrors(platform)
    tokens =>
      tokens
        .collect {
          case (targetSqs, harvestedToken) if targetSqs == workerSqs => harvestedToken.token
        }
        .chunkN(1000)
        .map(chunk => ChunkedTokens(shardedNotification.notification, chunk.toList, shardedNotification.range))
        .map(deliveryService.sending)
        .parJoin(maxConcurrency)
        .collect {
          case Left(throwable) => throwable
        }
        .through(platformSinkErrors)

  }

  def routeToSqs: PartialFunction[HarvestedToken, (WorkerSqs, HarvestedToken)] = {
    case token @ HarvestedToken(_, Ios, _) => (WorkerSqs.IosWorkerSqs, token)
    case token @ HarvestedToken(_, IosEdition, _) => (WorkerSqs.IosEditionWorkerSqs, token)
    case token @ HarvestedToken(_, Android, Some(BuildTier.BETA)) => (WorkerSqs.AndroidBetaWorkerSqs, token)
    case token @ HarvestedToken(_, Android, _) => (WorkerSqs.AndroidWorkerSqs, token)
    case token @ HarvestedToken(_, AndroidEdition, _) => (WorkerSqs.AndroidEditionWorkerSqs, token)
  }

  def queueShardedNotification(shardedNotifications: Stream[IO, ShardedNotification]): Stream[IO, Unit] = {
    for {
      shardedNotification <- shardedNotifications
      androidSink = platformSink(shardedNotification, Android, WorkerSqs.AndroidWorkerSqs, androidLiveDeliveryService)
      androidBetaSink = platformSink(shardedNotification, AndroidBeta, WorkerSqs.AndroidBetaWorkerSqs, androidBetaDeliveryService)
      androidEditionSink = platformSink(shardedNotification, AndroidEdition, WorkerSqs.AndroidEditionWorkerSqs, androidEditionDeliveryService)
      iosSink = platformSink(shardedNotification, Ios, WorkerSqs.IosWorkerSqs, iosLiveDeliveryService)
      iosEditionSink = platformSink(shardedNotification, IosEdition, WorkerSqs.IosEditionWorkerSqs, iosEditionDeliveryService)
      notificationLog = s"(notification: ${shardedNotification.notification.id} ${shardedNotification.range})"
      _ = logger.info(s"Queuing notification $notificationLog...")
      tokens = tokenService.tokens(shardedNotification.notification, shardedNotification.range)
      resp <- tokens
        .collect(routeToSqs)
        .broadcastTo(androidSink, androidBetaSink, androidEditionSink, iosSink, iosEditionSink)
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
