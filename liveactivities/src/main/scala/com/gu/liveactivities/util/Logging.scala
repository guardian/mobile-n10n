package com.gu.liveactivities.util

import net.logstash.logback.marker.{LogstashMarker, Markers}
import org.slf4j.{Logger, LoggerFactory}

trait Logging {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Builds a logstash marker that adds a `liveActivityID` field to a single log statement, e.g.
   * {{{ logger.info(liveActivityMarker(id), "Sending broadcast") }}}
   * The LogstashEncoder configured in logback.xml renders the marker as a top-level JSON field.
   */
  def liveActivityMarker(liveActivityId: String): LogstashMarker =
    Markers.append(Logging.LiveActivityIdKey, liveActivityId)
}

object Logging {
  val LiveActivityIdKey = "liveActivityID"
}
