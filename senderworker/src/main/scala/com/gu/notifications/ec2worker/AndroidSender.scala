package com.gu.notifications.ec2worker

import cats.effect.{ContextShift, IO, Timer}
import com.gu.notifications.worker.cleaning.CleaningClientImpl
import com.gu.notifications.worker.delivery.fcm.{Fcm, FcmClient}
import com.gu.notifications.worker.utils.{Cloudwatch, CloudwatchImpl}
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import com.gu.notifications.worker.SenderRequestHandler
import _root_.models.{Android, AndroidBeta, AndroidEdition, Ios, IosEdition, Platform}
import com.gu.notifications.worker.FcmWorkerConfiguration

class AndroidSender(androidPlatform: Platform) extends SenderRequestHandler[FcmClient] {

  val config: FcmWorkerConfiguration = Configuration.fetchFirebase(androidPlatform)
  val cleaningClient = new CleaningClientImpl(config.cleaningSqsUrl)
  val cloudwatch: Cloudwatch = new CloudwatchImpl

  override implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(config.threadPoolSize))

  logger.info(s"Using thread pool size: ${config.threadPoolSize}")

  override implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  override implicit val timer: Timer[IO] = IO.timer(ec)

  override val deliveryService: IO[Fcm[IO]] =
    FcmClient(config.fcmConfig).fold(e => IO.raiseError(e), c => IO.delay(new Fcm(c)))
  override val maxConcurrency = 100
}
