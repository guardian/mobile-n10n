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
case class NotificationIdField(value: UUID, name: String = "notificationId") extends LoggingField
case class NotificationTypeField(value: String, name: String = "notificationType") extends LoggingField
case class NotificationTitleField(value: String, name: String = "notificationTitle") extends LoggingField
case class ProcessingTimeField(value: Long, name: String = "processingTime") extends LoggingField
case class NotificationStartTimeField(value: Long, name: String = "notificationStartTime") extends LoggingField

trait NotificationLogging {
  def customLogstashFields(fields: List[LoggingField]): LogstashMarker = {
    val customFields = fields.map(field => (field.name, field.value)).toMap.asJava
    appendEntries(customFields)
  }

  def logInfoWithCustomMarkers(message: String, fields: List[LoggingField])(implicit logger: Logger): Unit =
    logger.info(customLogstashFields(fields), message)
}

object NotificationLogging extends NotificationLogging