package com.gu.notifications.worker

import com.gu.notifications.worker.utils.Logging
import org.slf4j.{Logger, LoggerFactory}

class ExpiredRegistrationCleanerLambda extends Logging {

  def logger: Logger = LoggerFactory.getLogger(this.getClass)

  def handleRequest(): Unit = {
    logger.info("Hello")
  }
}
