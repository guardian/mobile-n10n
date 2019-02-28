package com.gu.notifications.worker

import cats.effect.IO
import com.gu.notifications.worker.delivery.apns.{Apns, ApnsClient}
import db.{DatabaseConfig, RegistrationService}
import doobie.util.transactor.Transactor
import _root_.models.iOS
import com.gu.notifications.worker.cleaning.CleaningClientImpl
import com.gu.notifications.worker.tokens.{SqsDeliveryService, SqsDeliveryServiceImpl, TokenService, TokenServiceImpl}
import com.gu.notifications.worker.utils.{Cloudwatch, CloudwatchImpl}

class IOSWorker extends WorkerRequestHandler[ApnsClient] {
  val platform = iOS
  val config: ApnsWorkerConfiguration = Configuration.fetchApns()
  val transactor: Transactor[IO] = DatabaseConfig.transactor[IO](config.jdbcConfig)
  val registrationService = RegistrationService(transactor)
  val cleaningClient = new CleaningClientImpl(config.sqsUrl)
  val cloudwatch: Cloudwatch = new CloudwatchImpl
  val tokenServiceImpl = new TokenServiceImpl[IO](registrationService)
  val sqsDeliveryServiceImpl = new SqsDeliveryServiceImpl[IO](config.deliverySqsUrl)

  override val deliveryService: IO[Apns[IO]] =
    ApnsClient(config.apnsConfig).fold(e => IO.raiseError(e), c => IO.delay(new Apns(registrationService, c)))

  override val tokenService: IO[TokenService[IO]] = IO.delay(tokenServiceImpl)
  override val maxConcurrency = 100
  override val sqsDeliveryService: IO[SqsDeliveryService[IO]] = IO.delay(sqsDeliveryServiceImpl)
}

