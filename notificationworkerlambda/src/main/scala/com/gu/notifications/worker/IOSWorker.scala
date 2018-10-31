package com.gu.notifications.worker

import cats.effect.IO
import com.gu.notifications.worker.delivery.ApnsDeliverySuccess
import com.gu.notifications.worker.delivery.apns.{Apns, ApnsClient}

class IOSWorker extends WorkerRequestHandler[ApnsDeliverySuccess] {
  val config: ApnsWorkerConfiguration = Configuration.fetchApns()
  val deliveryService: IO[Apns[IO]] = ApnsClient(config.apnsConfig).fold(e => IO.raiseError(e), c => IO.delay(new Apns(registrationService, c)))
}

