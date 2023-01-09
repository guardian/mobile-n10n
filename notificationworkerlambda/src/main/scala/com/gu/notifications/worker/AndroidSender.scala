package com.gu.notifications.worker

import cats.effect.{ContextShift, IO, Timer}
import com.gu.notifications.worker.cleaning.CleaningClientImpl
import com.gu.notifications.worker.delivery.DeliveryException.InvalidToken
import com.gu.notifications.worker.delivery.apns.models.IOSMetricsRegistry
import com.gu.notifications.worker.delivery.{BatchDeliverySuccess, DeliveryClient, DeliveryException}
import com.gu.notifications.worker.delivery.fcm.{Fcm, FcmClient}
import com.gu.notifications.worker.models.SendingResults
import com.gu.notifications.worker.tokens.{BatchNotification, ChunkedTokens}
import com.gu.notifications.worker.utils.{Cloudwatch, CloudwatchImpl, Reporting}
import fs2.{Pipe, Stream}

import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class AndroidSender(val config: FcmWorkerConfiguration, val firebaseAppName: Option[String], val metricNs: String) extends SenderRequestHandler[FcmClient] {

  // maybe we'll implement a metrics registry for android in the future?
  // it seems like a metrics registry could be a nice way to abstract all the metrics we care about collecting
  val registry = new IOSMetricsRegistry
  def this() = {
    this(Configuration.fetchFirebase(), None, "workers")
  }

  val cleaningClient = new CleaningClientImpl(config.cleaningSqsUrl)
  val cloudwatch: Cloudwatch = new CloudwatchImpl(metricNs)

  override implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(config.threadPoolSize))

  logger.info(s"Using thread pool size: ${config.threadPoolSize}")

  override implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  override implicit val timer: Timer[IO] = IO.timer(ec)

  override val deliveryService: IO[Fcm[IO]] =
    FcmClient(config.fcmConfig, firebaseAppName).fold(e => IO.raiseError(e), c => IO.delay(new Fcm(c)))
  override val maxConcurrency = 100

  //override the deliverChunkedTokens method to validate the success of sending batch notifications to the FCM client. This implementation could be refactored in the future to make it more streamlined with APNs
  override def deliverChunkedTokens(chunkedTokenStream: Stream[IO, (ChunkedTokens, Long, Instant, Int)]): Stream[IO, Unit] = {
    chunkedTokenStream.map {
      case (chunkedTokens, sentTime, functionStartTime, sqsMessageBatchSize) =>
        logger.info(Map("notificationId" -> chunkedTokens.notification.id), s"Sending notification ${chunkedTokens.notification.id} to topics ${chunkedTokens.notification.topic} in batches")
        deliverBatchNotificationStream(Stream.emits(chunkedTokens.toBatchNotificationToSends).covary[IO])
          .broadcastTo(
            reportBatchSuccesses(chunkedTokens, sentTime, functionStartTime, sqsMessageBatchSize),
            cleanupBatchFailures(chunkedTokens.notification.id),
            trackBatchProgress(chunkedTokens.notification.id))
    }.parJoin(maxConcurrency)
  }

  def deliverBatchNotificationStream[C <: FcmClient](batchNotificationStream: Stream[IO, BatchNotification]): Stream[IO, Either[DeliveryException, C#BatchSuccess]] = for {
    deliveryService <- Stream.eval(deliveryService)
    resp <- batchNotificationStream.map(batchNotification => deliveryService.sendBatch(batchNotification.notification, batchNotification.token))
      .parJoin(maxConcurrency)
      .evalTap(Reporting.logBatch(s"Sending failure: "))
  } yield resp

  def reportBatchSuccesses[C <: DeliveryClient](chunkedTokens: ChunkedTokens, sentTime: Long, functionStartTime: Instant, sqsMessageBatchSize: Int): Pipe[IO, Either[DeliveryException, BatchDeliverySuccess], Unit] = { input =>
    val notificationLog = s"(notification: ${chunkedTokens.notification.id} ${chunkedTokens.range})"
    input
      .fold(SendingResults.empty) { case (acc, resp) => SendingResults.aggregateBatch(acc, chunkedTokens.tokens.size, resp) }
      .evalTap(logInfoWithFields(logFields(env, chunkedTokens.notification, chunkedTokens.tokens.size, sentTime, functionStartTime, Configuration.platform, isIndividualNotificationSend = false, sqsMessageBatchSize), prefix = s"Results $notificationLog: "))
      .through(cloudwatch.sendMetrics(env.stage, Configuration.platform))
  }

  def cleanupBatchFailures[C <: DeliveryClient](notificationId: UUID): Pipe[IO, Either[Throwable, BatchDeliverySuccess], Unit] = { input =>
    input
      .collect {
        case Right(batchSuccess) =>
          batchSuccess.responses.foldLeft(List[String]())((acc, res) => res match {
            case Left(InvalidToken(_, token, _, _)) =>
              logger.debug(Map("notificationId" -> notificationId), s"Invalid token $token")
              token :: acc
            case Left(_) => acc
            case Right(_) => acc
          })
      }
      .chunkN(1000)
      .through(cleaningClient.sendInvalidBatchTokensToCleaning)
  }

  def trackBatchProgress[C <: DeliveryClient](notificationId: UUID): Pipe[IO, Either[Throwable, BatchDeliverySuccess], Unit] = {
    input => input.evalMap(chunk => IO.delay(chunk match {
      case Left(_) => () // we log in other places if there's a catastrophic batch error
      case Right(batchSuccess) => logger.info(Map("notificationId" -> notificationId), s"Processed ${batchSuccess.responses.size} individual notification(s)")
    }))
  }
}
