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
import com.typesafe.config.Config
import com.gu.notifications.worker.AndroidSender

class Ec2AndroidSender(appConfig: Config, androidPlatform: Platform) extends AndroidSender {

  override val config: FcmWorkerConfiguration = Configuration.fetchFirebase(appConfig, androidPlatform)

  override val deliveryService: IO[Fcm[IO]] =
    FcmClient(config.fcmConfig, s"[${androidPlatform.toString()}]").fold(e => IO.raiseError(e), c => IO.delay(new Fcm(c)))
}
