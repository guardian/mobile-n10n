package com.gu.notifications.worker

import cats.effect.IO
import com.gu.notifications.worker.delivery.fcm.{Fcm, FcmClient}
import db.{DatabaseConfig, RegistrationService}
import doobie.util.transactor.Transactor
import _root_.models.Android
import com.gu.notifications.worker.cleaning.CleaningClient

class AndroidWorker extends WorkerRequestHandler[FcmClient] {
  val platform = Android
  val config: FcmWorkerConfiguration = Configuration.fetchFirebase()
  val transactor: Transactor[IO] = DatabaseConfig.transactor[IO](config.jdbcConfig)
  val registrationService = RegistrationService(transactor)
  val cleaningClient = new CleaningClient(config.sqsUrl)

  override val deliveryService: IO[Fcm[IO]] =
    FcmClient(config.fcmConfig).fold(e => IO.raiseError(e), c => IO.delay(new Fcm(registrationService, c)))
}

