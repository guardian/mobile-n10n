package com.gu.notifications.worker

import cats.effect.IO
import com.gu.notifications.worker.cleaning.CleaningClientImpl
import com.gu.notifications.worker.delivery.apns.{Apns, ApnsClient}
import com.gu.notifications.worker.utils.{Cloudwatch, CloudwatchImpl}

class IOSSender extends SenderRequestHandler[ApnsClient] {
  val config: ApnsWorkerConfiguration = Configuration.fetchApns()
  val cleaningClient = new CleaningClientImpl(config.cleaningSqsUrl)
  val cloudwatch: Cloudwatch = new CloudwatchImpl

  override val deliveryService: IO[Apns[IO]] =
    ApnsClient(config.apnsConfig).fold(e => IO.raiseError(e), c => IO.delay(new Apns(c)))
  override val maxConcurrency = 100

}

