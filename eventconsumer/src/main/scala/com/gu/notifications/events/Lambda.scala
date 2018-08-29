package com.gu.notifications.events

import java.io.{InputStream, OutputStream}

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.amazonaws.util.IOUtils
import org.apache.logging.log4j.LogManager
import play.api.libs.json.Json

object Lambda extends App {
  new Lambda("aws-mobile-event-logs-CODE")
}
class Lambda(eventConsumer: S3Event => Unit) extends RequestStreamHandler {
  val logger = LogManager.getLogger(classOf[Lambda])
  def this(eventBucket:String) = this(e => new ProcessEvents(eventBucket))
  def this() = this(System.getenv("EventBucket"))
  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    val inputString = try {
      IOUtils.toString(input)
    }
    finally {
      input.close()
    }
    logger.info(inputString)
    S3Event.jf.reads(Json.parse(inputString)).foreach(eventConsumer)
  }

}
