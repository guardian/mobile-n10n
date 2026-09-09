package com.gu.notifications.worker

import aws.TopicCountsS3
import cats.effect.{ContextShift, IO}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import com.gu.notifications.worker.utils.{Aws, Logging}
import db.{DatabaseConfig, RegistrationService}
import doobie.util.transactor.Transactor
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class TopicCounterLambda extends Logging {
  def logger: Logger = LoggerFactory.getLogger(this.getClass)

  def env = Env()

  lazy val s3Client: S3Client = S3Client.builder()
    .region(Region.EU_WEST_1)
    .credentialsProvider(Aws.credentialsProviderV2)
    .build()

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)

  val config: TopicCountsConfiguration = Configuration.fetchTopicCounter()
  val topicsS3 = new TopicCountsS3(s3Client, config.bucketName, s"${env.stage}/${config.fileName}")
  val transactor: Transactor[IO] = DatabaseConfig.transactor[IO](config.jdbcConfig)
  val registrationService = RegistrationService(transactor)
  val topicCounts = new TopicCounter(registrationService, topicsS3, config.countThreshold)

  def handleRequest(): Unit = {
    logger.info("Handling request to get topic counters: ")
    topicCounts.handleRequest()
    logger.info("Done")
  }

  def runLocally(): Unit = {
    topicCounts.handleRequest()
    s3Client.close()
  }
}
