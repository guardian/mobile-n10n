package com.gu.notifications.workerlambda.utils

import cats.effect.IO
import com.gu.notifications.workerlambda.delivery.DeliveryException.{FailedRequest, InvalidToken}
import com.gu.notifications.workerlambda.delivery.{DeliveryClient, DeliveryException, DeliverySuccess}
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
