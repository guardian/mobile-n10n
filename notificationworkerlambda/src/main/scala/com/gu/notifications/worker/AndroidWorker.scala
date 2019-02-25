package com.gu.notifications.worker

import cats.effect.IO
import com.gu.notifications.worker.delivery.fcm.{Fcm, FcmClient}
import db.{DatabaseConfig, RegistrationService}
import doobie.util.transactor.Transactor
import _root_.models.Android
import com.gu.notifications.worker.cleaning.CleaningClientImpl
import com.gu.notifications.worker.tokens.{SqsDeliveryService, SqsDeliveryServiceImpl, TokenService, TokenServiceImpl}
import com.gu.notifications.worker.utils.{Cloudwatch, CloudwatchImpl}

class AndroidWorker extends WorkerRequestHandler[FcmClient] {
  val platform = Android
  val config: FcmWorkerConfiguration = Configuration.fetchFirebase()
  val transactor: Transactor[IO] = DatabaseConfig.transactor[IO](config.jdbcConfig)
  val registrationService = RegistrationService(transactor)
  val cleaningClient = new CleaningClientImpl(config.sqsUrl)
  val cloudwatch: Cloudwatch = new CloudwatchImpl

  override val deliveryService: IO[Fcm[IO]] =
    FcmClient(config.fcmConfig).fold(e => IO.raiseError(e), c => IO.delay(new Fcm(registrationService, c)))

  override val tokenService: IO[TokenService[IO]] = IO.delay(new TokenServiceImpl[IO](registrationService))
  override val maxConcurrency = 100
  override val sqsDeliveryService: IO[SqsDeliveryService[IO]] = IO.delay(new SqsDeliveryServiceImpl[IO](config.deliverySqsUrl))
}

