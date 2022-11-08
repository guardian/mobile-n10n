package com.gu.notifications.worker.utils

import cats.effect.IO
import com.gu.notifications.worker.delivery.DeliveryException.{FailedRequest, InvalidToken}
import com.gu.notifications.worker.delivery.{BatchDeliverySuccess, DeliveryClient, DeliveryException, DeliverySuccess}
import org.slf4j.Logger

object Reporting {

  private def logMatchCase(response: Either[DeliveryException, DeliverySuccess], prefix: String)(implicit logger: Logger): Unit = response match {
    case Left(e: InvalidToken) => logger.warn(s"$prefix $e")
    case Left(e: FailedRequest) => logger.warn(s"$prefix $e", e.cause)
    case Left(e) => logger.error(prefix, e)
    case Right(_) => () // doing nothing when success
  }


  def log[C <: DeliveryClient](prefix: String)(implicit logger: Logger): Either[DeliveryException, DeliverySuccess] => IO[Unit] = { resp =>
    IO.delay {
      logMatchCase(resp, prefix)
    }
  }

  def logBatch[C <: DeliveryClient](prefix: String)(implicit logger: Logger): Either[DeliveryException, BatchDeliverySuccess] => IO[Unit] = { resp =>
    IO.delay {
      resp match {
        case Left(e) => logger.error(prefix, e)
        case Right(batchSuccess) => batchSuccess.responses.foreach(resp => logMatchCase(resp, prefix))
      }
    }
  }

}
