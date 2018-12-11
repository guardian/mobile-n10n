package com.gu.notifications.events.sqs

import com.gu.notifications.events.SqsEvent
import com.gu.notifications.events.s3.S3Event
import play.api.libs.json.Json

object SqsEventReader {
  def readSqsEventString(inputString: String): List[S3Event] = {
    val sqsEventJson = Json.parse(inputString)
    val sqsEvent = SqsEvent.jf.reads(sqsEventJson).get
    sqsEvent.Records.map(_.body).map(Json.parse).map(S3Event.jf.reads).map(_.get)
  }
}
