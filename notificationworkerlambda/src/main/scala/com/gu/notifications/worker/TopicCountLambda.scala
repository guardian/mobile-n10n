package com.gu.notifications.worker

import java.util.concurrent.{Executors, ScheduledExecutorService}

import cats.effect.{ContextShift, IO}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClientBuilder}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.gu.notifications.worker.utils.{Logging, TopicCountS3}
import db.{DatabaseConfig, RegistrationService}
import doobie.util.transactor.Transactor
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class TopicCountLambda extends Logging {

  def logger: Logger = LoggerFactory.getLogger(this.getClass)

  def env = Env()

  lazy val credentials: AWSCredentialsProviderChain = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("mobile"),
    DefaultAWSCredentialsProviderChain.getInstance
  )

  lazy val s3Client: AmazonS3 = AmazonS3ClientBuilder.standard
      .withRegion(Regions.EU_WEST_1)
        .withCredentials(credentials)
        .build()

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)

  val config: TopicCountsConfiguration = Configuration.fetchTopicCounter()
  logger.info(s"Cunf: $config")
  lazy val topicsS3 = new TopicCountS3(s3Client, config.bucketName, s"${env.stage}/${config.fileName}")
  val transactor: Transactor[IO] = DatabaseConfig.transactor[IO](config.jdbcConfig)
  val registrationService = RegistrationService(transactor)
  lazy val topicCounts = new TopicCounts(registrationService, topicsS3)

  def handleRequest() : Unit = {
    logger.info("Handlin request")
    //opicCounts.handleRequest()
    logger.info("Done")
  }

  def runLocally(): Unit = {
     logger.info("Running locally")
  }

  def withScheduledExecutorService(function: ScheduledExecutorService => Any) : Unit = {
    val scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    try {
      function(scheduledExecutorService)
    }
    finally {
      scheduledExecutorService.isShutdown()
    }
  }
}
