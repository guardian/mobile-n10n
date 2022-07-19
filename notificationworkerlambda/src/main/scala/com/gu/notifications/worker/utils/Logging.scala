package com.gu.notifications.worker.utils

import cats.effect.IO
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers.appendEntries
import org.slf4j.Logger

import scala.jdk.CollectionConverters.MapHasAsJava

trait Logging {
  def logger: Logger

  implicit def mapToContext(c: Map[String, _]): LogstashMarker = appendEntries(c.asJava)

  private def log[A](prefix: String, logging: String => Unit): A => IO[Unit] = a => IO.delay(logging(s"$prefix: ${a.toString}"))
  def logInfo[A](prefix: String = ""): A => IO[Unit] = log(prefix, logger.info)
  def logWarn[A](prefix: String = ""): A => IO[Unit] = log(prefix, logger.warn)
  def logError[A](prefix: String = ""): A => IO[Unit] = log(prefix, logger.error)
}
