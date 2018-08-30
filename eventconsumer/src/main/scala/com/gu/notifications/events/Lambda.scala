package com.gu.notifications.events

import java.io.{InputStream, OutputStream}

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.amazonaws.util.IOUtils
import org.apache.logging.log4j.LogManager
import play.api.libs.json.Json

object Lambda extends App {

  new Lambda().handleRequest(System.in, System.out, null)
}
class Lambda(eventConsumer: S3Event => Unit) extends RequestStreamHandler {
  val logger = LogManager.getLogger(classOf[Lambda])
  def this() = this(new ProcessEvents())
  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    val inputString = try {
      IOUtils.toString(input)
    }
    finally {
      input.close()
    }
    logger.info(inputString)
    S3Event.jf.reads(Json.parse(inputString)).foreach(e => {
      logger.info(e)
      eventConsumer(e)
    })
  }

}