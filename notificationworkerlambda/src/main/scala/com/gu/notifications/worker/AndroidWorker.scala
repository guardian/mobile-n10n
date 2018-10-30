package com.gu.notifications.worker

import java.io.{InputStream, OutputStream}

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import org.slf4j.{Logger, LoggerFactory}

object AndroidWorker extends RequestStreamHandler  {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    logger.info("yo!")
  }
}
