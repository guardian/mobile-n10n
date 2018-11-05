package com.gu.notifications.worker.utils

import cats.effect.IO
import com.gu.notifications.worker.delivery.{DeliveryClient, DeliveryException, DeliverySuccess}
import fs2.Pipe
import org.slf4j.Logger

object Reporting {

  def report[C <: DeliveryClient](prefix: String)(implicit logger: Logger): Pipe[IO, Either[DeliveryException, C#Success], Either[DeliveryException, C#Success]] =
    _.evalMap { resp =>
      IO.delay {
        resp match {
          case Left(e) => logger.error(s"$prefix $e") //TODO: send invalid token to queue
          case Right(_) => () // doing nothing when success
        }
        resp
      }
    }

}
