package com.gu.notifications.worker.utils

import cats.effect.IO
import com.gu.notifications.worker.Env
import models.{Notification, NotificationType, Platform}
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers.appendEntries
import org.slf4j.Logger

import java.time.{Duration, Instant}
import scala.jdk.CollectionConverters.MapHasAsJava

trait Logging {
  def logger: Logger

  implicit def mapToContext(c: Map[String, _]): LogstashMarker = appendEntries(c.asJava)

  private def log[A](prefix: String, logging: String => Unit): A => IO[Unit] = a => IO.delay(logging(s"$prefix: ${a.toString}"))
  private def logWithFields[A](fields: Instant => Map[String, _], prefix: String, logging: (LogstashMarker, String) => Unit): A => IO[Unit] = a => {
    val end = Instant.now
    IO.delay(logging(fields(end), s"$prefix: ${a.toString}"))
  }

  def logInfo[A](prefix: String = ""): A => IO[Unit] = log(prefix, logger.info)
  def logInfoWithFields[A](fields: Instant => Map[String, _], prefix: String = ""): A => IO[Unit] = logWithFields(fields, prefix, logger.info)
  def logWarn[A](prefix: String = ""): A => IO[Unit] = log(prefix, logger.warn)
  def logError[A](prefix: String = ""): A => IO[Unit] = log(prefix, logger.error)

  def logFields(
     env: Env,
     notification: Notification,
     numberOfTokens: Int,
     sentTime: Long,
     functionStartTime: Instant,
     maybePlatform: Option[Platform]
   )(end: Instant): Map[String, Any] = {
    val processingTime = Duration.between(functionStartTime, end).toMillis
    val processingRate = numberOfTokens.toDouble / processingTime * 1000
    val start = Instant.ofEpochMilli(sentTime)
    Map(
      "_aws" -> Map(
        "Timestamp" -> end.toEpochMilli,
        "CloudWatchMetrics" -> List(Map(
          "Namespace" -> s"Notifications/${env.stage}/workers",
          "Dimensions" -> List(List("platform", "type")),
          "Metrics" -> List(
            Map(
              "Name" -> "worker.notificationProcessingTime",
              "Unit" -> "Milliseconds"
            ),
            Map(
              "Name" -> "worker.functionProcessingRate"
            )
          )
        ))
      ),
      "notificationId" -> notification.id,
      "platform" -> maybePlatform.map(_.toString).getOrElse("unknown"),
      "type" -> {
        notification.`type` match {
          case NotificationType.BreakingNews => "breakingNews"
          case _                             => "other"
        }
      },
      "worker.functionProcessingRate" -> processingRate,
      "worker.functionProcessingTime" -> processingTime,
      "worker.notificationProcessingTime" -> Duration.between(start, end).toMillis,
      "worker.notificationProcessingStartTime.millis" -> sentTime,
      "worker.notificationProcessingEndTime.millis" -> end.toEpochMilli,
    )
  }
}
