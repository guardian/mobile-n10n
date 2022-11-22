package com.gu.notifications.worker

import cats.effect.{ContextShift, IO, Timer}
import com.gu.notifications.worker.cleaning.CleaningClientImpl
import com.gu.notifications.worker.delivery.apns.{Apns, ApnsClient}
import com.gu.notifications.worker.utils.{Cloudwatch, CloudwatchImpl}

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class IOSSender(val config: ApnsWorkerConfiguration, val metricNs: String) extends SenderRequestHandler[ApnsClient] {

  def this() = {
    this(Configuration.fetchApns(), "workers")
  }

  val cleaningClient = new CleaningClientImpl(config.cleaningSqsUrl)
  val cloudwatch: Cloudwatch = new CloudwatchImpl(metricNs)

  override implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(config.threadPoolSize))

  logger.info(s"Using thread pool size: ${config.threadPoolSize}")

  override implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  override implicit val timer: Timer[IO] = IO.timer(ec)

  override val deliveryService: IO[Apns[IO]] =
    ApnsClient(config.apnsConfig).fold(e => IO.raiseError(e), c => IO.delay(new Apns(c)))
  override val maxConcurrency = 100

}
