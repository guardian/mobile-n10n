package com.gu.notifications.worker.utils

import cats.effect.IO
import com.gu.notifications.worker.delivery.DeliveryException.{FailedRequest, InvalidToken}
import com.gu.notifications.worker.delivery.{DeliveryClient, DeliveryException, DeliverySuccess}
import org.slf4j.Logger

object Reporting {

  def log[C <: DeliveryClient](prefix: String)(implicit logger: Logger): Either[DeliveryException, DeliverySuccess] => IO[Unit] = { resp =>
    IO.delay {
      resp match {
        case Left(e: InvalidToken) => logger.warn(s"$prefix $e")
        case Left(e: FailedRequest) => logger.warn(s"$prefix $e", e.cause)
        case Left(e) => logger.error(prefix, e)
        case Right(_) => () // doing nothing when success
      }
    }
  }

}
