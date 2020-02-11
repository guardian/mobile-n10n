package com.gu.notifications.worker

import cats.effect.{ContextShift, IO}
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.gu.notifications.worker.utils.Aws
import db.{DatabaseConfig, RegistrationService}
import doobie.util.transactor.Transactor
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class ExpiredRegistrationCleaner {
  def logger: Logger = LoggerFactory.getLogger(this.getClass)

  def clean(): Unit = {
    logger.info("Hello")
  }
}

class ExpiredRegistrationCleanerLambda {
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  def env = Env()

  lazy val credentials: AWSCredentialsProviderChain = Aws.credentialsProvider

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)

  val config: CleanerConfiguration = Configuration.fetchCleaner()
  val transactor: Transactor[IO] = DatabaseConfig.transactor[IO](config.jdbcConfig)

  val registrationService = RegistrationService(transactor)

  def handleRequest(): Unit = {
    val olderThanDays = 300
    val rowCount = registrationService.deleteByDate(olderThanDays).unsafeRunSync()
    logger.info(s"Deleted $rowCount rows older than $olderThanDays days")
  }
}

object ExpiredRegistrationCleanerLocalRun {
  def main(args: Array[String]): Unit = {
    val cleaner = new ExpiredRegistrationCleanerLambda
    cleaner.handleRequest()
  }
}
