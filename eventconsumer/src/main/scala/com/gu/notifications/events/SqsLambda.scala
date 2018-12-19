package com.gu.notifications.events

import java.io.{InputStream, OutputStream}
import java.util.concurrent.ForkJoinPool

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.amazonaws.util.IOUtils
import com.gu.notifications.events.dynamo.DynamoReportUpdater
import com.gu.notifications.events.s3.S3EventProcessorImpl
import org.apache.logging.log4j.{LogManager, Logger}
import play.api.libs.json._

import scala.concurrent.ExecutionContext

case class Record(body: String)

object Record {
  implicit val jf = Json.format[Record]
}

case class SqsEvent(Records: List[Record])

object SqsEvent {
  implicit val jf = Json.format[SqsEvent]
}


class SqsLambda(stage: String) extends RequestStreamHandler {
  private val logger: Logger = LogManager.getLogger(classOf[SqsLambda])
  private val reportUpdater = new DynamoReportUpdater(stage)
  implicit private val executionContext: ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool(5))
  private val s3EventProcessor = new S3EventProcessorImpl
  private val router = new Router(s3EventProcessor, reportUpdater)

  def this() = this(System.getenv().getOrDefault("Stage", "CODE"))

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    try {
      val inputString = try {
        IOUtils.toString(input)
      }
      finally {
        input.close()
      }
      router.sqsEventRoute(inputString)
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