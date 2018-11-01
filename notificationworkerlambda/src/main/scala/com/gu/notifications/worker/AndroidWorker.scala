package com.gu.notifications.worker

import cats.effect.IO
import com.gu.notifications.worker.delivery.FcmDeliverySuccess
import com.gu.notifications.worker.delivery.fcm.{Fcm, FcmClient}
import db.{DatabaseConfig, RegistrationService}
import doobie.util.transactor.Transactor

class AndroidWorker extends WorkerRequestHandler[FcmDeliverySuccess] {
  val config: FcmWorkerConfiguration = Configuration.fetchFirebase()
  val transactor: Transactor[IO] = DatabaseConfig.transactor[IO](config.jdbcConfig)
  val registrationService = RegistrationService(transactor)

  override val deliveryService: IO[Fcm[IO]] =
    FcmClient(config.fcmConfig).fold(e => IO.raiseError(e), c => IO.delay(new Fcm(registrationService, c)))
}

