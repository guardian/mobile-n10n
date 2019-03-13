package com.gu.notifications.worker

import cats.effect.{ContextShift, IO}
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.gu.notifications.worker.utils.{Aws, Logging, TopicCountsS3}
import db.{DatabaseConfig, RegistrationService}
import doobie.util.transactor.Transactor
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class TopicCounterLambda extends Logging {
  def logger: Logger = LoggerFactory.getLogger(this.getClass)

  def env = Env()

  lazy val credentials: AWSCredentialsProviderChain = Aws.credentialsProvider

  lazy val s3Client: AmazonS3 = AmazonS3ClientBuilder.standard
    .withRegion(Regions.EU_WEST_1)
    .withCredentials(credentials)
    .build()

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)

  val config: TopicCountsConfiguration = Configuration.fetchTopicCounter()
  val topicsS3 = new TopicCountsS3(s3Client, config.bucketName, s"${env.stage}/${config.fileName}")
  val transactor: Transactor[IO] = DatabaseConfig.transactor[IO](config.jdbcConfig)
  val registrationService = RegistrationService(transactor)
  val topicCounts = new TopicCounter(registrationService, topicsS3)

  def handleRequest(): Unit = {
    logger.info("Handling request to get topic counters: ")
    topicCounts.handleRequest()
    logger.info("Done")
  }

  def runLocally(): Unit = {
    topicCounts.handleRequest()
    s3Client.shutdown()
  }
}
