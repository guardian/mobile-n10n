package com.gu.notifications.worker

import java.util.concurrent.{Executors, ScheduledExecutorService}

import cats.effect.{ContextShift, IO}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClientBuilder}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.gu.notifications.worker.utils.{Logging, TopicCountsS3}
import db.{DatabaseConfig, RegistrationService}
import doobie.util.transactor.Transactor
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class TopicCounterLambda extends Logging {
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
  lazy val topicsS3 = new TopicCountsS3(s3Client, config.bucketName, s"${env.stage}/${config.fileName}")
  val transactor: Transactor[IO] = DatabaseConfig.transactor[IO](config.jdbcConfig)
  val registrationService = RegistrationService(transactor)
  val topicCounts = new TopicCounter(registrationService, topicsS3)

  def handleRequest() : Unit = {
    logger.info("Handling request to get topic counts: ")
    topicCounts.handleRequest()
    logger.info("Topic counts Done")
  }

  def runLocally(): Unit = {
    topicCounts.handleRequest()
    s3Client.shutdown()
  }

  def withS3Client(function: AmazonS3 => Any)