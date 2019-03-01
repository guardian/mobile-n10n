package com.gu.notifications.worker

import java.util.UUID

import _root_.models.{Platform, ShardRange}
import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.worker.cleaning.CleaningClient
import com.gu.notifications.worker.delivery.DeliveryException.InvalidToken
import com.gu.notifications.worker.delivery._
import com.gu.notifications.worker.models.SendingResults
import com.gu.notifications.worker.tokens.{ChunkedTokens, IndividualNotification, SqsDeliveryService}
import com.gu.notifications.worker.utils.{Cloudwatch, Logging, NotificationParser, Reporting}
import fs2.{Sink, Stream}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


trait SenderRequestHandler[C <: DeliveryClient] extends Logging {

  def platform: Platform

  def deliveryService: IO[DeliveryService[IO, C]]

  val cleaningClient: CleaningClient
  val cloudwatch: Cloudwatch
  val maxConcurrency: Int

  def env = Env()

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def reportSuccesses[C <: DeliveryClient](notificationId: UUID, range: ShardRange): Sink[IO, Either[DeliveryException, DeliverySuccess]] = { input =>
    val notificationLog = s"(notification: ${notificationId} ${range})"
    input.fold(SendingResults.empty) { case (acc, resp) => SendingResults.aggregate(acc, resp) }
      .evalTap(logInfo(prefix = s"Results $notificationLog: "))
      .to(cloudwatch.sendMetrics(env.stage, platform))
  }

  def cleanupFailures[C <: DeliveryClient]: Sink[IO, Either[DeliveryException, DeliverySuccess]] = { input =>
    input
      .collect {
        case Left(InvalidToken(_, token, _, _)) =>
          logger.debug(s"Invalid token $token")
          token
      }
      .chunkN(1000)
      .to(cleaningClient.sendInvalidTokensToCleaning)
  }

  def deliverIndividualNotificationStream(individualNotificationStream: Stream[IO, IndividualNotification]): Stream[IO, Either[DeliveryException, C#Success]] = for {
    deliveryService <- Stream.eval(deliveryService)
    resp <- individualNotificationStream.map(individualNotification => deliveryService.send(individualNotification.notification, individualNotification.token, individualNotification.platform))
      .parJoin(maxConcurrency)
      .evalTap(Reporting.log(s"Sending failure: "))
  } yield resp

  def deliverChunkedTokens(chunkedTokenStream: Stream[IO, ChunkedTokens]) = {
    for {
      chunkedTokens <- chunkedTokenStream
      individualNotifications = Stream.emits(chunkedTokens.toNotificationToSends).covary[IO]
      resp <- deliverIndividualNotificationStream(individualNotifications)
        .broadcastTo(reportSuccesses(chunkedTokens.notification.id, chunkedTokens.range), cleanupFailures)
    } yield resp
  }

  def handleChunkTokens(event: SQSEvent, context: Context): Unit = {
    val chunkedTokenStream: Stream[IO, ChunkedTokens] = Stream.emits(event.getRecords.asScala)
      .map(r => r.getBody)
      .map(NotificationParser.parseChunkedTokenEvent)

    deliverChunkedTokens(chunkedTokenStream)
      .compile
      .drain
      .unsafeRunSync()
  }
}
