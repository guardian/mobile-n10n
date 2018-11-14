package com.gu.notifications.worker.utils

import cats.effect.IO
import com.gu.notifications.worker.delivery.DeliveryException.DryRun
import com.gu.notifications.worker.delivery.{DeliveryClient, DeliveryException, DeliverySuccess}
import org.slf4j.Logger

object Reporting {

  def log[C <: DeliveryClient](prefix: String)(implicit logger: Logger): Either[DeliveryException, DeliverySuccess] => IO[Unit] = { resp =>
    IO.delay {
      resp match {
        case Left(DryRun(_, _)) => () // no need to trace each individual token in the logs
        case Left(e) => logger.error(s"$prefix $e")
        case Right(_) => () // doing nothing when success
      }
    }
  }

}
