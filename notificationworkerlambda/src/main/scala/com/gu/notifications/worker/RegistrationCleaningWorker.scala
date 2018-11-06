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

import scala.collection.JavaConverters._
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

    def traverseAndDelete(remainingTokens: List[String], total: CleaningResult): IO[CleaningResult] = remainingTokens match {
      case token :: moreTokens => deleteAndSwallowError(token).flatMap { deletionResult =>
        traverseAndDelete(moreTokens, total.combined(deletionResult))
      }
      case Nil => IO.pure(total)
    }

    val tokens = input
      .getRecords.asScala
      .map(_.getBody)
      .map(Json.parse)
      .flatMap(_.validate[InvalidTokens].asOpt)
      .foldLeft(List.empty[String]){ case (agg, value) => agg ++ value.tokens }

    val processReport = traverseAndDelete(tokens, CleaningResult(0, 0, 0)).unsafeRunSync()

    logger.info(s"Deleted ${processReport.deletedRows} rows, deleted ${processReport.deletedRegistrations} registration and failed ${processReport.failures} time(s)")
  }
}
