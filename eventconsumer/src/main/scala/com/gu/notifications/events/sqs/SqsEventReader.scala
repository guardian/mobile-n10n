package com.gu.notifications.events.sqs

import com.gu.notifications.events.SqsEvent
import com.gu.notifications.events.s3.S3Event
import org.apache.logging.log4j.LogManager
import play.api.libs.json.{JsError, JsSuccess, Json}

import scala.util.{Success, Try, Failure}

object SqsEventReader {
  val logger = LogManager.getLogger(SqsEventReader.getClass)
  def readSqsEventString(inputString: String): List[Try[S3Event]] = {
    val sqsEventJson = Json.parse(inputString)
    val sqsEvent = SqsEvent.jf.reads(sqsEventJson).get
    sqsEvent.Records.map(_.body).map(Json.parse).map{ jsValue =>
      S3Event.jf.reads(jsValue) match {
        case JsSuccess(value, _) => Success(value)
        case JsError(errors) => {
          val errorMessage = s"Error parsing: $jsValue\nFound errors: $errors\nFrom original json $sqsEventJson"
          logger.warn(errorMessage)
          Failure(new RuntimeException(errorMessage))
        }
      }
    }
  }
}
