package com.gu.notifications.workerlambda

import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.workerlambda.cleaning.CleaningClient
import com.gu.notifications.workerlambda.delivery.DeliveryException.InvalidToken
import com.gu.notifications.workerlambda.delivery.{DeliveryClient, DeliveryException, DeliveryService, DeliverySuccess}
import com.gu.notifications.workerlambda.tokens.{ChunkedTokens, IndividualNotification}
import com.gu.notifications.workerlambda.models.ShardRange
import com.gu.notifications.workerlambda.utils.{Cloudwatch, Logging, NotificationParser, Reporting}
import fs2.{Pipe, Stream}
import models.SendingResults
import org.slf4j.{Logger, LoggerFactory}

import java.time.{Duration, Instant}
import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

trait SenderRequestHandler[C <: DeliveryClient] extends Logging {

  def deliveryService: IO[DeliveryService[IO, C]]

  val cleaningClient: CleaningClient
  val cloudwatch: Cloudwatch
  val maxConcurrency: Int

  def env = Env()

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def reportSuccesses[C <: DeliveryClient](notificationId: UUID, range: ShardRange, start: Instant): Pipe[IO, Either[DeliveryException, DeliverySuccess], Unit] = { input =>
    val notificationLog = s"(notification: ${notificationId} ${range})"
    val end = Instant.now
    val logFields = Map(
      "notificationId" -> notificationId,
      "worker.notificationProcessingTime" -> Duration.between(start, end).toMillis,
      "worker.notificationProcessingStartTime.millis" -> start.toEpochMilli,
      "worker.notificationProcessingStartTime.string" -> start.toString,
      "worker.notificationProcessingEndTime.millis" -> end.toEpochMilli,
      "worker.notificationProcessingEndTime.string" -> end.toString,
    )
    input.fold(SendingResults.empty) { case (acc, resp) => SendingResults.aggregate(acc, resp) }
      .evalTap(logInfoWithFields(logFields, prefix = s"Results $notificationLog: "))
      .through(cloudwatch.sendMetrics(env.stage, Configuration.platform))
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

  def deliverChunkedTokens(chunkedTokenStream: Stream[IO, ChunkedTokens], start: Instant): Stream[IO, Unit] = {
    for {
      chunkedTokens <- chunkedTokenStream
      individualNotifications = Stream.emits(chunkedTokens.toNotificationToSends).covary[IO]
      resp <- deliverIndividualNotificationStream(individualNotifications)
        .broadcastTo(reportSuccesses(chunkedTokens.notification.id, chunkedTokens.range, start), cleanupFailures, trackProgress(chunkedTokens.notification.id))
    } yield resp
  }

  def handleChunkTokens(event: SQSEvent): Unit = {
    val start = Instant.now
    val chunkedTokenStream: Stream[IO, ChunkedTokens] = Stream.emits(event.getRecords.asScala)
      .map(r => r.getBody)
      .map(NotificationParser.parseChunkedTokenEvent)

    deliverChunkedTokens(chunkedTokenStream, start)
      .compile
      .drain
      .unsafeRunSync()
  }
}
