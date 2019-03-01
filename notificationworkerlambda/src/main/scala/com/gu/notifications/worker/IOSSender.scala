package com.gu.notifications.worker

import _root_.models.iOS
import cats.effect.IO
import com.gu.notifications.worker.cleaning.CleaningClientImpl
import com.gu.notifications.worker.delivery.apns.{Apns, ApnsClient}
import com.gu.notifications.worker.tokens.{SqsDeliveryService, SqsDeliveryServiceImpl}
import com.gu.notifications.worker.utils.{Cloudwatch, CloudwatchImpl}

class IOSSender extends SenderRequestHandler[ApnsClient] {
  val platform = iOS
  val config: ApnsWorkerConfiguration = Configuration.fetchApns()
  val cleaningClient = new CleaningClientImpl(config.sqsUrl)
  val cloudwatch: Cloudwatch = new CloudwatchImpl
  val sqsDeliveryServiceImpl = new SqsDeliveryServiceImpl[IO](config.deliverySqsUrl)

  override val deliveryService: IO[Apns[IO]] =
    ApnsClient(config.apnsConfig).fold(e => IO.raiseError(e), c => IO.delay(new Apns(c)))

  override val maxConcurrency = 100
  override val sqsDeliveryService: IO[SqsDeliveryService[IO]] = IO.delay(sqsDeliveryServiceImpl)
}

