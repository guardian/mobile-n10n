package com.gu.notifications.worker

import cats.effect.IO
import com.gu.notifications.worker.delivery.FcmDeliverySuccess
import com.gu.notifications.worker.delivery.fcm.{Fcm, FcmClient}

class AndroidWorker extends WorkerRequestStreamHandler[FcmDeliverySuccess] {
  val config: FcmWorkerConfiguration = Configuration.fetchFirebase()
  val deliveryServiceIO: IO[Fcm[IO]] = FcmClient(config.fcmConfig).fold(e => IO.raiseError(e), c => IO.delay(new Fcm(registrationService, c)))
}

