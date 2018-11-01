package com.gu.notifications.worker.utils

import cats.effect.IO
import fs2.Pipe
import org.slf4j.Logger

trait Logging {
  def logger: Logger

  private def log[A](prefix: String, logging: String => Unit): Pipe[IO, A, A] = _.evalMap{ a => IO.delay{ logging(s"$prefix: ${a.toString}"); a}}
  def logInfo[A](prefix: String = ""): Pipe[IO, A, A] = log(prefix, logger.info)
  def logWarn[A](prefix: String = ""): Pipe[IO, A, A] = log(prefix, logger.warn)
  def logError[A](prefix: String = ""): Pipe[IO, A, A] = log(prefix, logger.error)
}
