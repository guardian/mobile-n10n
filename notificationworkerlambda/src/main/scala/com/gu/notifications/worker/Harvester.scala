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
import org.threeten.bp.ZoneOffset

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.jdk.CollectionConverters._
import java.time.{Duration, Instant, LocalDateTime, ZoneId}

sealed trait WorkerSqs
object WorkerSqs {
  case object AndroidWorkerSqs extends WorkerSqs
  case object AndroidBetaWorkerSqs extends WorkerSqs
  case object AndroidEditionWorkerSqs extends WorkerSqs
  case object IosWorkerSqs extends WorkerSqs
  case object IosEditionWorkerSqs extends WorkerSqs
}

case class SqsDeliveryStack(
  iosLiveDeliveryService: SqsDeliveryService[IO],
  iosEditionDeliveryService: SqsDeliveryService[IO],
  androidLiveDeliveryService: SqsDeliveryService[IO],
  androidEditionDeliveryService: SqsDeliveryService[IO],
  androidBetaDeliveryService: SqsDeliveryService[IO]
)

trait HarvesterRequestHandler extends Logging {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val env = Env()

  val lambdaServiceSet: SqsDeliveryStack
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
        .map(chunk => ChunkedTokens(shardedNotification.notification, chunk.toList, shardedNotification.range, shardedNotification.metadata))
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
    for {
      shardedNotification <- shardedNotifications
      notificationId = shardedNotification.notification.id
      androidSink = platformSink(shardedNotification, Android, WorkerSqs.AndroidWorkerSqs, lambdaServiceSet.androidLiveDeliveryService)
      androidBetaSink = platformSink(shardedNotification, AndroidBeta, WorkerSqs.AndroidBetaWorkerSqs, lambdaServiceSet.androidBetaDeliveryService)
      androidEditionSink = platformSink(shardedNotification, AndroidEdition, WorkerSqs.AndroidEditionWorkerSqs, lambdaServiceSet.androidEditionDeliveryService)
      iosSink = platformSink(shardedNotification, Ios, WorkerSqs.IosWorkerSqs, lambdaServiceSet.iosLiveDeliveryService)
      iosEditionSink = platformSink(shardedNotification, IosEdition, WorkerSqs.IosEditionWorkerSqs, lambdaServiceSet.iosEditionDeliveryService)
      notificationLog = s"(notification: $notificationId ${shardedNotification.range})"
      _ = logger.info(Map("notificationId" -> notificationId), s"Queuing notification $notificationLog...")
      tokens = tokenService.tokens(shardedNotification.notification, shardedNotification.range)
      resp <- tokens
        .collect(routeToSqs)
        .broadcastTo(androidSink, androidBetaSink, androidEditionSink, iosSink, iosEditionSink)
    } yield resp
  }

  def processNotification(event: SQSEvent, tokenService: TokenService[IO]) = {
    val records = event.getRecords.asScala.toList.map(r => (NotificationParser.parseShardNotificationEvent(r.getBody), r.getAttributes))
    records.foreach {
      case (body, _) => logger.info(Map(
        "notificationId" -> body.notification.id,
      ), "Parsed notification event")
    }
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
        records.foreach {
          case (body, _) =>
            logger.error(Map(
              "notificationId" -> body.notification.id,
              "notificationType" -> body.notification.`type`.toString,
            ), s"Error occurred: ${e.getMessage}", e)
        }
        throw e
      }
    }finally {
      records.foreach {
        case (body, attributes) => {
          val end = Instant.now
          val sentTime = Instant.ofEpochMilli(attributes.getOrDefault("SentTimestamp", "0").toLong)

          logger.info(Map(
            "_aws" -> Map(
              "Timestamp" -> end.toEpochMilli,
              "CloudWatchMetrics" -> List(Map(
                "Namespace" -> s"Notifications/${env.stage}/harvester",
                "Dimensions" -> List(List("type")),
                "Metrics" -> List(Map(
                  "Name" -> "harvester.notificationProcessingTime",
                  "Unit" -> "Milliseconds"
                ))
              ))
            ),
            "harvester.notificationProcessingTime" -> Duration.between(sentTime, end).toMillis,
            "harvester.notificationProcessingEndTime.millis" -> end.toEpochMilli,
            "harvester.notificationProcessingStartTime.millis" -> sentTime.toEpochMilli,
            "notificationId" -> body.notification.id,
            "notificationType" -> body.notification.`type`.toString,
            "type" -> {
              body.notification.`type` match {
                case _root_.models.NotificationType.BreakingNews => "breakingNews"
                case _ => "other"
              }
            }
          ), "Finished processing notification event")
        }
      }
    }
  }

  def handleHarvesting(event: SQSEvent, context: Context): Unit = {
    // open connection
    val (transactor, datasource): (Transactor[IO], HikariDataSource) = DatabaseConfig.transactorAndDataSource[IO](jdbcConfig)
    logger.info("Java version: " + System.getProperty("java.version"))

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

  override val cloudwatch: Cloudwatch = new CloudwatchImpl("workers")

  override val lambdaServiceSet = SqsDeliveryStack(
   iosLiveDeliveryService = new SqsDeliveryServiceImpl[IO](config.iosLiveSqsUrl),
   androidLiveDeliveryService = new SqsDeliveryServiceImpl[IO](config.androidLiveSqsUrl),
   iosEditionDeliveryService = new SqsDeliveryServiceImpl[IO](config.iosEditionSqsUrl),
   androidEditionDeliveryService = new SqsDeliveryServiceImpl[IO](config.androidEditionSqsUrl),
   androidBetaDeliveryService = new SqsDeliveryServiceImpl[IO](config.androidBetaSqsUrl)
  )
}
    
