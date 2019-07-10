package com.gu.notifications.worker

import cats.effect.IO
import com.gu.notifications.worker.cleaning.CleaningClientImpl
import com.gu.notifications.worker.delivery.fcm.{Fcm, FcmClient}
import com.gu.notifications.worker.utils.{Cloudwatch, CloudwatchImpl}

class AndroidSender extends SenderRequestHandler[FcmClient] {
  val config: FcmWorkerConfiguration = Configuration.fetchFirebase()
  val cleaningClient = new CleaningClientImpl(config.sqsUrl)
  val cloudwatch: Cloudwatch = new CloudwatchImpl

  override val deliveryService: IO[Fcm[IO]] =
    FcmClient(config.fcmConfig).fold(e => IO.raiseError(e), c => IO.delay(new Fcm(c)))
  override val maxConcurrency = 100
}

