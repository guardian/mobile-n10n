package com.gu.notifications.events

import java.io.{InputStream, OutputStream}
import java.util.concurrent.ForkJoinPool

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.amazonaws.util.IOUtils
import org.apache.logging.log4j.LogManager
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

object Lambda extends App {
  new Lambda().handleRequest(System.in, System.out, null)
}
class Lambda(eventConsumer: ProcessEvents)(implicit executionContext: ExecutionContext) extends RequestStreamHandler {
  val logger = LogManager.getLogger(classOf[Lambda])
  def this() = this(new ProcessEventsImpl())(ExecutionContext.fromExecutor(new ForkJoinPool(25)))

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    try {
      val inputString = try {
        IOUtils.toString(input)
      }
      finally {
        input.close()
      }
      S3Event.jf.reads(Json.parse(inputString)).foreach(e => {
        eventConsumer(e)
      })
    }
      catch {
        case t: Throwable =>
          logger.warn("Error running lambda", t)
          throw t
      }
    finally {
      output.close()
    }
  }

}