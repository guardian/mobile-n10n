package com.gu.notifications.worker.utils

import cats.effect.IO
import com.gu.notifications.worker.Env
import models.{Notification, NotificationType, Platform}
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers.appendEntries
import org.slf4j.Logger

import java.time.{Duration, Instant}
import scala.jdk.CollectionConverters.MapHasAsJava
import com.gu.notifications.worker.models.PerformanceMetrics
import com.gu.notifications.worker.tokens.ChunkedTokens

trait Logging {
  def logger: Logger

  implicit def mapToContext(c: Map[String, _]): LogstashMarker = appendEntries(c.asJava)

  private def log[A](prefix: String, logging: String => Unit): A => IO[Unit] = a => IO.delay(logging(s"$prefix: ${a.toString}"))
  private def logWithFields[A](fields: Instant => Map[String, _], prefix: String, logging: (LogstashMarker, String) => Unit): A => IO[PerformanceMetrics] = a => {
    val end = Instant.now
    IO.delay {
      val metricsField = fields(end)
      logging(metricsField, s"$prefix: ${a.toString}")
      PerformanceMetrics(
        notificationId = metricsField.get("notificationId").get.toString(),
        platform = metricsField.get("platform").get.toString(),
        notificationType = metricsField.get("type").get.toString(),
        functionProcessingRate = metricsField.get("worker.functionProcessingRate").get.asInstanceOf[Double],
        functionProcessingTime = metricsField.get("worker.functionProcessingTime").get.asInstanceOf[Long],
        notificationProcessingTime = metricsField.get("worker.notificationProcessingTime").get.asInstanceOf[Long],
        notificationProcessingStartTime = metricsField.get("worker.notificationProcessingStartTime.millis").get.asInstanceOf[Long],
        notificationProcessingEndTime = metricsField.get("worker.notificationProcessingEndTime.millis").get.asInstanceOf[Long],
        sqsMessageBatchSize = metricsField("sqsMessageBatchSize").asInstanceOf[Int],
        chunkTokenSize = metricsField("worker.chunkTokenSize").asInstanceOf[Int]
      )
    }
  }

  def logInfo[A](prefix: String = ""): A => IO[Unit] = log(prefix, logger.info)
  def logInfoWithFields[A](fields: Instant => Map[String, _], prefix: String = ""): A => IO[PerformanceMetrics] = logWithFields(fields, prefix, logger.info)
  def logWarn[A](prefix: String = ""): A => IO[Unit] = log(prefix, logger.warn)
  def logError[A](prefix: String = ""): A => IO[Unit] = log(prefix, logger.error)

  def logFields(
    env: Env,
    notification: Notification,
    numberOfTokens: Int,
    sentTime: Long,
    functionStartTime: Instant,
    maybePlatform: Option[Platform],
    sqsMessageBatchSize: Int,
    messagingApi: String,
    awsRequestId: String,
  )(end: Instant): Map[String, Any] = {
    val processingTime = Duration.between(functionStartTime, end).toMillis
    val processingRate = numberOfTokens.toDouble / processingTime * 1000
    val start = Instant.ofEpochMilli(sentTime)

    Map(
      "notificationId" -> notification.id,
      "platform" -> maybePlatform.map(_.toString).getOrElse("unknown"),
      "type" -> Reporting.notificationTypeForObservability(notification),
      "worker.functionProcessingRate" -> processingRate,
      "worker.functionProcessingTime" -> processingTime,
      "worker.notificationProcessingTime" -> Duration.between(start, end).toMillis,
      "worker.notificationProcessingStartTime.millis" -> sentTime,
      "worker.notificationProcessingEndTime.millis" -> end.toEpochMilli,
      "sqsMessageBatchSize" -> sqsMessageBatchSize,
      "worker.chunkTokenSize" -> numberOfTokens,
      "worker.messagingApi" -> messagingApi,
      "awsRequestId" -> awsRequestId,
    )
  }

  def logStartAndCount(awsRequestId: String)(acc: Int, chunkedTokens: ChunkedTokens): Int = {
    logger.info(Map(
      "notificationId" -> chunkedTokens.notification.id,
      "awsRequestId" -> awsRequestId,
    ), "Start processing a SQS message");
    acc + chunkedTokens.tokens.size
  }

  def logEndOfInvocation(awsRequestId: String)(sqsMessageBatchSize: Int, totalTokensProcessed: Int, startTime: Instant): Unit =
    logger.info(Map(
      "sqsMessageBatchSize" -> sqsMessageBatchSize,
      "totalTokensProcessed" -> totalTokensProcessed,
      "invocation.functionProcessingRate" -> {
        totalTokensProcessed.toDouble / Duration.between(startTime, Instant.now).toMillis * 1000
      },
      "awsRequestId" -> awsRequestId,
    ), "Processed all messages from SQS event")
}
