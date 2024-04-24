package com.gu.notifications.worker

import _root_.models.NotificationMetadata
import java.util.UUID
import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.worker.cleaning.CleaningClient
import com.gu.notifications.worker.delivery.DeliveryException.InvalidToken
import com.gu.notifications.worker.delivery._
import models.{LatencyMetrics, SendingResults}
import com.gu.notifications.worker.tokens.{ChunkedTokens, IndividualNotification}
import com.gu.notifications.worker.utils.{Cloudwatch, Logging, NotificationParser, Reporting}
import fs2.{Pipe, Stream}
import org.slf4j.{Logger, LoggerFactory}

import java.time.Instant
import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


trait SenderRequestHandler[C <: DeliveryClient] extends Logging {

  def deliveryService: IO[DeliveryService[IO, C]]

  val cleaningClient: CleaningClient
  val cloudwatch: Cloudwatch
  val maxConcurrency: Int
  val batchConcurrency: Int

  def env = Env()

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def reportSuccesses[C <: DeliveryClient](chunkedTokens: ChunkedTokens, sentTime: Long, functionStartTime: Instant, sqsMessageBatchSize: Int): Pipe[IO, Either[DeliveryException, DeliverySuccess], Unit] = { input =>
    val notificationLog = s"(notification: ${chunkedTokens.notification.id} ${chunkedTokens.range})"
    val enableAwsMetric = chunkedTokens.notification.dryRun match {
      case Some(true) => false
      case _          => true
    }

    input.fold(SendingResults.empty) { case (acc, resp) => SendingResults.aggregate(acc, resp) }
      .evalTap(logInfoWithFields(logFields(env, chunkedTokens.notification, chunkedTokens.tokens.size, sentTime, functionStartTime, Configuration.platform, sqsMessageBatchSize = sqsMessageBatchSize, messagingApi = Some("Individual")), prefix = s"Results $notificationLog: ").andThen(_.map(cloudwatch.sendPerformanceMetrics(env.stage, enableAwsMetric))))
      .through(cloudwatch.sendResults(env.stage, Configuration.platform))
  }

  def reportLatency[C <: DeliveryClient](chunkedTokens: ChunkedTokens, metadata: NotificationMetadata): Pipe[IO, Either[DeliveryException, DeliverySuccess], Unit] = { input =>
    val shouldPushMetricsToAws = chunkedTokens.notification.dryRun match {
      case Some(true) => false
      case _ => true
    }
    input
      .fold(List.empty[Long]) { case (acc, resp) => LatencyMetrics.collectLatency(acc, resp, metadata.notificationAppReceivedTime) }
      .through(cloudwatch.sendLatencyMetrics(shouldPushMetricsToAws, env.stage, Configuration.platform, metadata.audienceSize))
  }

  def trackProgress[C <: DeliveryClient](notificationId: UUID): Pipe[IO, Either[DeliveryException, DeliverySuccess], Unit] = { input =>
    input.chunkN(100).evalMap(chunk => IO.delay(logger.info(Map("notificationId" -> notificationId), s"Processed ${chunk.size} individual notification")))
  }

  def cleanupFailures[C <: DeliveryClient]: Pipe[IO, Either[DeliveryException, DeliverySuccess], Unit] = { input =>
    input
      .collect {
        case Left(InvalidToken(_, token, _, _)) =>
          logger.debug(s"Invalid token $token")
          token
      }
      .chunkN(1000)
      .through(cleaningClient.sendInvalidTokensToCleaning)
  }

  def deliverIndividualNotificationStream(individualNotificationStream: Stream[IO, IndividualNotification]): Stream[IO, Either[DeliveryException, C#Success]] = for {
    deliveryService <- Stream.eval(deliveryService)
    resp <- individualNotificationStream.map(individualNotification => deliveryService.send(individualNotification.notification, individualNotification.token))
      .parJoin(maxConcurrency)
      .evalTap(Reporting.log(s"Sending failure: "))
  } yield resp

  def deliverChunkedTokens(chunkedTokenStream: Stream[IO, (ChunkedTokens, Long, Instant, Int)]): Stream[IO, Unit] = {
    chunkedTokenStream.map {
      case (chunkedTokens, sentTime, functionStartTime, sqsMessageBatchSize) =>
        val individualNotifications = Stream.emits(chunkedTokens.toNotificationToSends).covary[IO]
        deliverIndividualNotificationStream(individualNotifications)
          .broadcastTo(
            reportSuccesses(chunkedTokens, sentTime, functionStartTime, sqsMessageBatchSize),
            reportLatency(chunkedTokens, chunkedTokens.metadata),
            cleanupFailures,
            trackProgress(chunkedTokens.notification.id))
      }.parJoin(batchConcurrency)
  }

  def handleChunkTokens(event: SQSEvent, context: Context): Unit = {
    val sqsMessageBatchSize = event.getRecords.size
    val startTime = Instant.now
    val totalTokensProcessed: Int = event.getRecords.asScala.map(event => NotificationParser.parseChunkedTokenEvent(event.getBody)).foldLeft(0)(logStartAndCount)
    val chunkedTokenStream: Stream[IO, (ChunkedTokens, Long, Instant, Int)] = Stream.emits(event.getRecords.asScala)
      .map(r => (r.getBody, r.getAttributes.getOrDefault("SentTimestamp", "0").toLong))
      .map { case (body, sentTimestamp) => (NotificationParser.parseChunkedTokenEvent(body), sentTimestamp, startTime, sqsMessageBatchSize) }

    deliverChunkedTokens(chunkedTokenStream)
      .compile
      .drain
      .unsafeRunSync()

    logEndOfInvocation(sqsMessageBatchSize, totalTokensProcessed, startTime)
  }
}
