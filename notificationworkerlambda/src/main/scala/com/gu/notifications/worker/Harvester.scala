package com.gu.notifications.worker

import _root_.models._
import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.worker.tokens._
import com.gu.notifications.worker.utils.{Cloudwatch, CloudwatchImpl, Logging, NotificationParser}
import com.zaxxer.hikari.HikariDataSource
import db._
import doobie.util.transactor.Transactor
import fs2.{Pipe, Stream}
import org.slf4j.{Logger, LoggerFactory}

import java.time.{Duration, Instant}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.jdk.CollectionConverters._

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

  val jdbcConfig: JdbcConfig
  val cloudwatch: Cloudwatch
  val maxConcurrency: Int = 100
  val supportedPlatforms = List(Ios, Android, IosEdition, AndroidEdition)

  def logErrors(prefix: String = ""): Pipe[IO, Throwable, Unit] = throwables => {
    throwables.map(throwable => logger.warn(s"${prefix}Error queueing", throwable))
  }

  def sinkErrors(platform: Platform): Pipe[IO, Throwable, Unit] = throwables => {
    throwables.broadcastTo(logErrors(s"platform:[${platform}] "), cloudwatch.sendFailures(env.stage, platform))
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

  def queueShardedNotification(shardedNotifications: Stream[IO, ShardedNotification], tokenService: TokenService[IO]): Stream[IO, Unit] = {
    val start = Instant.now
    for {
      shardedNotification <- shardedNotifications
      notificationId = shardedNotification.notification.id
      androidSink = platformSink(shardedNotification, Android, WorkerSqs.AndroidWorkerSqs, androidLiveDeliveryService)
      androidBetaSink = platformSink(shardedNotification, AndroidBeta, WorkerSqs.AndroidBetaWorkerSqs, androidBetaDeliveryService)
      androidEditionSink = platformSink(shardedNotification, AndroidEdition, WorkerSqs.AndroidEditionWorkerSqs, androidEditionDeliveryService)
      iosSink = platformSink(shardedNotification, Ios, WorkerSqs.IosWorkerSqs, iosLiveDeliveryService)
      iosEditionSink = platformSink(shardedNotification, IosEdition, WorkerSqs.IosEditionWorkerSqs, iosEditionDeliveryService)
      notificationLog = s"(notification: $notificationId ${shardedNotification.range})"
      _ = logger.info(Map("notificationId" -> notificationId), s"Queuing notification $notificationLog...")
      tokens = tokenService.tokens(shardedNotification.notification, shardedNotification.range)
      resp <- tokens
        .collect(routeToSqs)
        .broadcastTo(androidSink, androidBetaSink, androidEditionSink, iosSink, iosEditionSink)
      _ = logger.info(Map(
        "notificationId" -> notificationId,
        "harvester.processingTime" -> Duration.between(start, Instant.now).toMillis
      ), s"Finished processing notificationId $notificationId")
    } yield resp
  }

  def processNotification(event: SQSEvent, tokenService: TokenService[IO]) = {
    val shardNotificationStream: Stream[IO, ShardedNotification] = Stream.emits(event.getRecords.asScala)
      .map(r => r.getBody)
      .map(NotificationParser.parseShardNotificationEvent)
    try{
      queueShardedNotification(shardNotificationStream, tokenService)
        .compile
        .drain
        .unsafeRunSync()
    }catch {
      case e: Throwable => {
        logger.error(s"Error occurred: ${e.getMessage}", e)
        throw e
      }
    }
  }

  def handleHarvesting(event: SQSEvent, context: Context): Unit = {
    // open connection
    val (transactor, datasource): (Transactor[IO], HikariDataSource) = DatabaseConfig.transactorAndDataSource[IO](jdbcConfig)
    logger.info("SQL connection open")

    // create services that rely on the connection
    val registrationService: RegistrationService[IO, Stream] = RegistrationService(transactor)
    val tokenService: TokenServiceImpl[IO] = new TokenServiceImpl[IO](registrationService)

    processNotification(event, tokenService)

    // close connection
    datasource.close()
    logger.info("SQL connection closed")
  }
}


class Harvester extends HarvesterRequestHandler {
  val config: HarvesterConfiguration = Configuration.fetchHarvester()

  override val jdbcConfig: JdbcConfig = config.jdbcConfig

  override val cloudwatch: Cloudwatch = new CloudwatchImpl
  override val iosLiveDeliveryService: SqsDeliveryService[IO] = new SqsDeliveryServiceImpl[IO](config.iosLiveSqsUrl)
  override val androidLiveDeliveryService: SqsDeliveryService[IO] = new SqsDeliveryServiceImpl[IO](config.androidLiveSqsUrl)
  override val iosEditionDeliveryService: SqsDeliveryService[IO] = new SqsDeliveryServiceImpl[IO](config.iosEditionSqsUrl)
  override val androidEditionDeliveryService: SqsDeliveryService[IO] = new SqsDeliveryServiceImpl[IO](config.androidEditionSqsUrl)
  override val androidBetaDeliveryService: SqsDeliveryService[IO] = new SqsDeliveryServiceImpl[IO](config.androidBetaSqsUrl)
}
