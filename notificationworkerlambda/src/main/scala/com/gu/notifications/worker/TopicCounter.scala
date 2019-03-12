package com.gu.notifications.worker

import cats.effect.IO
import com.amazonaws.services.s3.model.PutObjectResult
import fs2.Stream
import com.gu.notifications.worker.utils.{Logging, TopicCountsS3}
import db.RegistrationService
import _root_.models.TopicCount
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Format

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

class TopicCounter(registrationService: RegistrationService[IO, Stream], topicCountS3: TopicCountsS3) extends Logging {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def handleRequest()(implicit format: Format[TopicCount], executionContext: ExecutionContext): Unit = {

    val topicCountStream = registrationService.topicCounts
    val ioTopicListF = topicCountStream.compile.toList.unsafeToFuture

    val ioTopicList = Await.result(ioTopicListF, Duration.Inf)
    logger.info(s"Retrieved ${ioTopicList.length} topic counts of over 1000")

    topicCountS3.put(ioTopicList)
 }
}
