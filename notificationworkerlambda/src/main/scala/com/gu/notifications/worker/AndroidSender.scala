package com.gu.notifications.worker

import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.worker.cleaning.CleaningClientImpl
import com.gu.notifications.worker.delivery.DeliveryException
import com.gu.notifications.worker.delivery.fcm.{Fcm, FcmClient}
import com.gu.notifications.worker.tokens.{BatchNotification, ChunkedTokens}
import com.gu.notifications.worker.utils.{Cloudwatch, CloudwatchImpl, NotificationParser, Reporting}
import fs2.Stream

import java.time.Instant
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.jdk.CollectionConverters._


class AndroidSender(val config: FcmWorkerConfiguration, val firebaseAppName: Option[String]) extends SenderRequestHandler[FcmClient] {

  def this() = {
    this(Configuration.fetchFirebase(), None)
  }

  val cleaningClient = new CleaningClientImpl(config.cleaningSqsUrl)
  val cloudwatch: Cloudwatch = new CloudwatchImpl

  override implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(config.threadPoolSize))

  logger.info(s"Using thread pool size: ${config.threadPoolSize}")

  override implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  override implicit val timer: Timer[IO] = IO.timer(ec)

  override val deliveryService: IO[Fcm[IO]] =
    FcmClient(config.fcmConfig, firebaseAppName).fold(e => IO.raiseError(e), c => IO.delay(new Fcm(c)))
  override val maxConcurrency = 100

  //override the deliverChunkedTokens method to validate the success of sending batch notifications to the FCM client. This implementation could be refactored in the future to make it more streamlined with APNs
  override def deliverChunkedTokens(chunkedTokenStream: Stream[IO, (ChunkedTokens, Long, Instant)]): Stream[IO, Unit] = {
    for {
      (chunkedTokens, sentTime, functionStartTime) <- chunkedTokenStream
      isAllowedToSendBatch = chunkedTokens.notification.topic.forall(topic => config.allowedTopicsForBatchSend.contains(topic.toString))
      resp <- {
        val resultStream = if (isAllowedToSendBatch) {
          logger.info(s"Allowed to send notification ${chunkedTokens.notification.id} to topics ${chunkedTokens.notification.topic} in batches")
          deliverBatchNotificationStream(Stream.emits(chunkedTokens.toBatchNotificationToSends).covary[IO])
        }
        else
          deliverIndividualNotificationStream(Stream.emits(chunkedTokens.toNotificationToSends).covary[IO])

        resultStream
          .broadcastTo(
            reportSuccesses(chunkedTokens, sentTime, functionStartTime),
            cleanupFailures,
            trackProgress(chunkedTokens.notification.id))
      }
    } yield resp
  }

  def deliverBatchNotificationStream[C <: FcmClient](batchNotificationStream: Stream[IO, BatchNotification]): Stream[IO, Either[DeliveryException, C#Success]] = for {
    deliveryService <- Stream.eval(deliveryService)
    resp <- batchNotificationStream.map(batchNotification => deliveryService.sendBatch(batchNotification.notification, batchNotification.token))
      .parJoin(maxConcurrency)
      .evalTap(Reporting.log(s"Sending failure: "))
  } yield resp
}
