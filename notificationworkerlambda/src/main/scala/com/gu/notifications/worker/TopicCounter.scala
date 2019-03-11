package com.gu.notifications.worker

import cats.effect.IO
import com.amazonaws.services.s3.model.PutObjectResult
import fs2.Stream
import com.gu.notifications.worker.utils.{Logging, TopicCountsS3}
import db.RegistrationService
import models.TopicCount
import models.TopicCount.topicCountJf
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Format

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

class TopicCounter(registrationService: RegistrationService[IO, Stream], topicCountS3: TopicCountsS3) extends Logging {

  def logger: Logger = LoggerFactory.getLogger(this.getClass)

  def handleRequest()(implicit format: Format[TopicCount], executionContext: ExecutionContext): Unit = {

    val topicCountStream = registrationService.topicCounts
    val ioTopicList = topicCountStream.compile.toList.unsafeToFuture

    val x = Await.result(ioTopicList, Duration.Inf)
    logger.info(s"Retrieved ${x.length} topic counts of over 1000")

    topicCountS3.put(x)
 }
}
