package com.gu.notifications.events

import java.io.{InputStream, OutputStream}

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.amazonaws.util.IOUtils
import org.apache.logging.log4j.{LogManager, Logger}
import play.api.libs.json._


object SqsLambda extends App {
  new SqsLambda().handleRequest(System.in, System.out, null)
}
case class Record(body: String)
object Record {
  implicit val jf = Json.format[Record]
}
case class SqsEvent(Records: List[Record])
object SqsEvent {
  implicit val jf = Json.format[SqsEvent]
}
class SqsLambda extends RequestStreamHandler {

  private val logger: Logger = LogManager.getLogger(classOf[Lambda])
  private val lambda = new Lambda()
  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    try {
      val inputString = try {
        IOUtils.toString(input)
      }
      finally {
        input.close()
      }
      val sqsEventJson = Json.parse(inputString)
      SqsEvent.jf.reads(sqsEventJson).foreach(sqsEvent => {
        sqsEvent.Records.foreach(record => {
          val s3EventJson = Json.parse(record.body)
          lambda.processS3EventJson(s3EventJson)
        })
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