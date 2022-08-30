package com.gu.notifications.worker

import java.util.UUID
import _root_.models.{Notification, NotificationType, ShardRange}
import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.worker.cleaning.CleaningClient
import com.gu.notifications.worker.delivery.DeliveryException.InvalidToken
import com.gu.notifications.worker.delivery._
import models.SendingResults
import com.gu.notifications.worker.tokens.{ChunkedTokens, IndividualNotification}
import com.gu.notifications.worker.utils.{Cloudwatch, Logging, NotificationParser, Reporting}
import fs2.{Pipe, Stream}
import org.slf4j.{Logger, LoggerFactory}

import java.time.{Duration, Instant}
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

  def reportSuccesses[C <: DeliveryClient](notification: Notification, range: ShardRange, sentTime: Long): Pipe[IO, Either[DeliveryException, DeliverySuccess], Unit] = { input =>
    val notificationLog = s"(notification: ${notification.id} $range)"
    val start = Instant.ofEpochMilli(sentTime)
    val end = Instant.now
    val logFields = Map(
      "_aws" -> Map(
        "Timestamp" -> end.toEpochMilli,
        "CloudWatchMetrics" -> List(Map(
          "Namespace" -> s"Notifications/${env.stage}/workers",
          "Dimensions" -> List(List("platform", "type")),
          "Metrics" -> List(Map(
            "Name" -> "worker.notificationProcessingTime",
            "Unit" -> "Milliseconds"
          ))
        ))
      ),
      "notificationId" -> notification.id,
      "platform" -> Configuration.platform.map(_.toString).getOrElse("unknown"),
      "type" -> {
        notification.`type` match {
          case NotificationType.BreakingNews => "breakingNews"
          case _ => "other"
        }
      },
      "worker.notificationProcessingTime" -> Duration.between(start, end).toMillis,
      "worker.notificationProcessingStartTime.millis" -> sentTime,
      "worker.notificationProcessingEndTime.millis" -> end.toEpochMilli,
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

  def deliverChunkedTokens(chunkedTokenStream: Stream[IO, (ChunkedTokens, Long)]): Stream[IO, Unit] = {
    for {
      (chunkedTokens, sentTime) <- chunkedTokenStream
      individualNotifications = Stream.emits(chunkedTokens.toNotificationToSends).covary[IO]
      resp <- deliverIndividualNotificationStream(individualNotifications)
        .broadcastTo(reportSuccesses(chunkedTokens.notification, chunkedTokens.range, sentTime), cleanupFailures, trackProgress(chunkedTokens.notification.id))
    } yield resp
  }

  def handleChunkTokens(event: SQSEvent, context: Context): Unit = {
    val chunkedTokenStream: Stream[IO, (ChunkedTokens, Long)] = Stream.emits(event.getRecords.asScala)
      .map(r => {
        val sentTimestamp = r.getAttributes.getOrDefault("SentTimestamp", "0").toLong
        logger.info(s"sent timestamp of message: $sentTimestamp")
        (r.getBody, r.getAttributes.getOrDefault("SentTimestamp", "0").toLong)
      })
      .map { case (body, sentTimestamp) => (NotificationParser.parseChunkedTokenEvent(body), sentTimestamp) }

    deliverChunkedTokens(chunkedTokenStream)
      .compile
      .drain
      .unsafeRunSync()
  }
}
