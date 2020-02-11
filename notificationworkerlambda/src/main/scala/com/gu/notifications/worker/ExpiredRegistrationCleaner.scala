package com.gu.notifications.worker

import com.gu.notifications.worker.utils.Logging
import org.slf4j.{Logger, LoggerFactory}

class ExpiredRegistrationCleaner {
  def logger: Logger = LoggerFactory.getLogger(this.getClass)

  def clean(): Unit = {
    logger.info("Hello")
  }
}

class ExpiredRegistrationCleanerLambda {
  def handleRequest(): Unit = {
    val cleaner = new ExpiredRegistrationCleaner
    cleaner.clean()
  }
}

object ExpiredRegistrationCleanerLocalRun {
  def main(args: Array[String]): Unit = {
    val cleaner = new ExpiredRegistrationCleaner
    cleaner.clean()
  }
}
