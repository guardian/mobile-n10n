package com.gu.notifications.worker

import cats.effect.IO
import com.gu.notifications.worker.delivery.apns.{Apns, ApnsClient}
import db.{DatabaseConfig, RegistrationService}
import doobie.util.transactor.Transactor

class IOSWorker extends WorkerRequestHandler[ApnsClient] {
  val config: ApnsWorkerConfiguration = Configuration.fetchApns()
  val transactor: Transactor[IO] = DatabaseConfig.transactor[IO](config.jdbcConfig)
  val registrationService = RegistrationService(transactor)

  override val deliveryService: IO[Apns[IO]] =
    ApnsClient(config.apnsConfig).fold(e => IO.raiseError(e), c => IO.delay(new Apns(registrationService, c)))
}

