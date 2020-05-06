package com.gu.notifications.worker

import cats.effect.{ContextShift, IO}
import cats.effect.IO._
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.notifications.worker.models.InvalidTokens
import db.{DatabaseConfig, RegistrationService}
import doobie.util.transactor.Transactor
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import fs2.Stream

import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.control.NonFatal

class RegistrationCleaningWorker extends RequestHandler[SQSEvent, Unit] {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  val config: CleanerConfiguration = Configuration.fetchCleaner()
  val transactor: Transactor[IO] = DatabaseConfig.transactor[IO](config.jdbcConfig)
  val registrationService = RegistrationService(transactor)

  case class CleaningResult(
    deletedRegistrations: Int,
    deletedRows: Int,
    failures: Int) {
    def combined(cleaningResult: CleaningResult): CleaningResult = this.copy(
      deletedRegistrations = cleaningResult.deletedRegistrations + deletedRegistrations,
      deletedRows = cleaningResult.deletedRows + deletedRows,
      failures = cleaningResult.failures + failures
    )
  }

  override def handleRequest(input: SQSEvent, context: Context): Unit = {

    def deleteAndSwallowError(token: String): IO[CleaningResult] = {
      registrationService.removeAllByToken(token)
        .map(deletedRows => CleaningResult(1, deletedRows, 0))
        .handleErrorWith {
          case NonFatal(e) =>
            logger.error(s"Unable to delete token $token", e)
            IO.pure(CleaningResult(0, 0, 1))
        }
    }

    def printResult(result: CleaningResult): IO[Unit] = IO {
      logger.info(s"Deleted ${result.deletedRows} rows, deleted ${result.deletedRegistrations} registration and failed ${result.failures} time(s)")
    }

    val tokens = input
      .getRecords.asScala
      .map(_.getBody)
      .map(Json.parse)
      .flatMap(_.validate[InvalidTokens].asOpt)
      .flatMap(_.tokens)
      .toList

    Stream
      .emits(tokens).covary[IO]
      .evalMap(deleteAndSwallowError)
      .reduce(_ combined _)
      .evalTap(printResult)
      .compile
      .drain
      .unsafeRunSync()
  }
}
