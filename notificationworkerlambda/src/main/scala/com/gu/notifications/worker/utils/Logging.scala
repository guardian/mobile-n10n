package com.gu.notifications.worker.utils

import cats.effect.IO
import org.slf4j.{Logger, Marker}
import software.amazon.lambda.powertools.logging.LoggingUtils

import models.LoggingField
import models.NotificationLogging.customLogstashFields

trait Logging {
  def logger: Logger

  private def log[A](prefix: String, logging: String => Unit): A => IO[Unit] = a => IO.delay(logging(s"$prefix: ${a.toString}"))
  private def logWithCustomFields[A](prefix: String, notificationId: String, logging: String => Unit): A => IO[Unit] =
    a => IO.delay({
      LoggingUtils.appendKey("notificationId", notificationId)
      logging(s"$prefix: ${a.toString}")
    })
  def logInfo[A](prefix: String = ""): A => IO[Unit] = log(prefix, logger.info)
  def logWarn[A](prefix: String = ""): A => IO[Unit] = log(prefix, logger.warn)
  def logError[A](prefix: String = ""): A => IO[Unit] = log(prefix, logger.error)
  def logInfoWithCustomFields[A](prefix: String = "", notificationId: String): A => IO[Unit] =
    logWithCustomFields(prefix, notificationId, logger.info)
}
