package models

import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers.appendEntries
import org.slf4j.Logger

import java.util.UUID

import scala.jdk.CollectionConverters.MapHasAsJava

sealed trait LoggingField {
  val name: String
  val value: Any
}
case class NotificationIdMarker(value: UUID, name: String = "notificationId") extends LoggingField
case class NotificationTypeMarker(value: String, name: String = "notificationType") extends LoggingField
case class NotificationTitleMarker(value: String, name: String = "notificationTitle") extends LoggingField
case class ProcessingTimeMarker(value: Long, name: String = "processingTime") extends LoggingField

trait NotificationLogging {
  private def customLogstashMarkers(fields: List[LoggingField]): LogstashMarker = {
    val customLogstashMarkers = fields.map(field => (field.name, field.value)).toMap.asJava
    appendEntries(customLogstashMarkers)
  }

  def logInfoWithCustomMarkers(message: String, fields: List[LoggingField])(implicit logger: Logger): Unit =
    logger.info(customLogstashMarkers(fields), message)
}

object NotificationLogging extends NotificationLogging