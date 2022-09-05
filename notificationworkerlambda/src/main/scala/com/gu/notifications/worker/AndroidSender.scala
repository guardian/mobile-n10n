package com.gu.notifications.worker

import cats.effect.{ContextShift, IO, Timer}
import com.gu.notifications.worker.cleaning.CleaningClientImpl
import com.gu.notifications.worker.delivery.DeliveryException
import com.gu.notifications.worker.delivery.fcm.{Fcm, FcmClient}
import com.gu.notifications.worker.tokens.{BatchNotification, ChunkedTokens, IndividualNotification}
import com.gu.notifications.worker.utils.{Cloudwatch, CloudwatchImpl, Reporting}
import fs2.Stream

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


class AndroidSender extends SenderRequestHandler[FcmClient] {
  val config: FcmWorkerConfiguration = Configuration.fetchFirebase()
  val cleaningClient = new CleaningClientImpl(config.cleaningSqsUrl)
  val cloudwatch: Cloudwatch = new CloudwatchImpl

  override implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(config.threadPoolSize))

  logger.info(s"Using thread pool size: ${config.threadPoolSize}")

  override implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  override implicit val timer: Timer[IO] = IO.timer(ec)

  override val deliveryService: IO[Fcm[IO]] =
    FcmClient(config.fcmConfig).fold(e => IO.raiseError(e), c => IO.delay(new Fcm(c)))
  override val maxConcurrency = 100

  //override the deliverChunkedTokens method to validate the success of sending batch notifications to the FCM client. This implementation could be refactored in the future to make it more streamlined with APNs
  override def deliverChunkedTokens(chunkedTokenStream: Stream[IO, (ChunkedTokens, Long)]): Stream[IO, Unit] = {
    for {
      (chunkedTokens, sentTime) <- chunkedTokenStream
      batchNotifications = Stream.emits(chunkedTokens.toBatchNotificationToSends).covary[IO]
      resp <- deliverIndividualNotificationStream(batchNotifications)
        .broadcastTo(reportSuccesses(chunkedTokens.notification, chunkedTokens.range, sentTime), cleanupFailures, trackProgress(chunkedTokens.notification.id))
    } yield resp
  }

  override def deliverIndividualNotificationStream(batchNotificationStream: Stream[IO, BatchNotification]): Stream[IO, Either[DeliveryException, C#Success]] = for {
    deliveryService <- Stream.eval(deliveryService)
    resp <- batchNotificationStream.map(batchNotification => deliveryService.send(batchNotification.notification, batchNotification.token))
      .parJoin(maxConcurrency)
      .evalTap(Reporting.log(s"Sending failure: "))
  } yield resp
}
