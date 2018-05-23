package com.gu.notificationshards.lambda

import java.io.{InputStream, OutputStream}

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.gu.notificationshards.cloudwatch.CloudWatchPublisher
import org.apache.logging.log4j.{LogManager, Logger}

import scala.util.Try

abstract class AwsLambda(function: String => String, logger: Logger = LogManager.getLogger(classOf[AwsLambda]), cloudWatch: CloudWatchPublisher) extends RequestStreamHandler {

  private val lambda: LambdaImpl = new LambdaImpl(function)

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = Try {
    lambda.execute(input, output)
    cloudWatch.sendMetricsSoFar()
  }.recover {
    case t: Throwable =>
      logger.warn(s"Error executing lambda", t)
      throw t
  }
}
