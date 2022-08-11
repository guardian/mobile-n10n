package com.gu.notifications.slos

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import org.slf4j.{Logger, LoggerFactory}
class SloMonitor {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def handleMessage(event: SQSEvent): Unit = {
    logger.info(s"Running SLO monitor")
  }

}