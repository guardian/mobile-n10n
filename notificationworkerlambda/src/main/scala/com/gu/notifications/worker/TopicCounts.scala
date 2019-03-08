package com.gu.notifications.worker

import cats.effect.IO
import com.amazonaws.services.s3.model.PutObjectResult
import fs2.Stream
import com.gu.notifications.worker.utils.{Logging, TopicCountS3}
import db.RegistrationService
import models.TopicCount
import models.TopicCount.topicCountJf
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Format

class TopicCounts(registrationService: RegistrationService[IO, Stream], topicCountS3: TopicCountS3) extends Logging {

  def logger: Logger = LoggerFactory.getLogger(this.getClass)

  def handleRequest()(implicit format: Format[TopicCount]): IO[PutObjectResult] = {

    logger.info("Fetching topic countrs")
    val topicCountStream = registrationService.topicCounts
    val ioTopicList = topicCountStream.compile.toList
    val l = ioTopicList.map {
      topicCounts =>
        logger.info(s"Got topic counts ${topicCounts.size}")
        topicCountS3.put(topicCounts)
    }
    l
 }
}
