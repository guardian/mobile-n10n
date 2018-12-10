package com.gu.notifications.worker.utils

import cats.effect.IO
import org.slf4j.Logger

trait Logging {
  def logger: Logger

  private def log[A](prefix: String, logging: String => Unit): A => IO[Unit] = a => IO.delay(logging(s"$prefix: ${a.toString}"))
  def logInfo[A](prefix: String = ""): A => IO[Unit] = log(prefix, logger.info)
  def logWarn[A](prefix: String = ""): A => IO[Unit] = log(prefix, logger.warn)
  def logError[A](prefix: String = ""): A => IO[Unit] = log(prefix, logger.error)
}
