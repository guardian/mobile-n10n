package com.gu.notifications.athena

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.athena.AmazonAthenaAsync
import com.amazonaws.services.athena.model.{GetQueryExecutionRequest, GetQueryExecutionResult, GetQueryResultsRequest, GetQueryResultsResult, QueryExecutionContext, QueryExecutionState, ResultConfiguration, StartQueryExecutionRequest, StartQueryExecutionResult}
import org.slf4j.{Logger, LoggerFactory}

import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.util.concurrent.{ScheduledExecutorService, TimeUnit}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._

case class Query(database: String, queryString: String, outputLocation: String)

object Athena {

  import ExecutionContext.Implicits.global

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val queuedString: String = QueryExecutionState.QUEUED.toString
  val runningString: String = QueryExecutionState.RUNNING.toString

  def toQueryDate(zonedDateTime: ZonedDateTime): String = {
    zonedDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
  }

  def asyncHandle[REQ <: AmazonWebServiceRequest, RES](asyncHandlerConsumer: AsyncHandler[REQ, RES] => Any): Future[RES] = {
    val promise = Promise[RES]()
    asyncHandlerConsumer(new AsyncHandler[REQ, RES] {
      override def onError(exception: Exception): Unit = promise.failure(exception)

      override def onSuccess(request: REQ, result: RES): Unit = promise.success(result)
    })
    promise.future
  }

  def startQuery(query: Query)(implicit athenaAsyncClient: AmazonAthenaAsync, scheduledExecutorService: ScheduledExecutorService): Future[String] = {
    def executeQuery(query: Query): Future[StartQueryExecutionResult] = asyncHandle[StartQueryExecutionRequest, StartQueryExecutionResult](asyncHandler =>
      athenaAsyncClient.startQueryExecutionAsync(new StartQueryExecutionRequest()
        .withQueryString(query.queryString)
        .withQueryExecutionContext(new QueryExecutionContext().withDatabase(query.database))
        .withResultConfiguration(new ResultConfiguration().withOutputLocation(query.outputLocation)), asyncHandler))

    def waitUntilQueryCompletes(id: String): Future[Unit] = {
      val promiseInASecond = Promise[Unit]()
      val runnable: Runnable = () => promiseInASecond.success(())
      scheduledExecutorService.schedule(runnable, 1, TimeUnit.SECONDS)
      promiseInASecond.future.flatMap { _ =>
        asyncHandle[GetQueryExecutionRequest, GetQueryExecutionResult](asyncHandler =>
          athenaAsyncClient.getQueryExecutionAsync(new GetQueryExecutionRequest().withQueryExecutionId(id), asyncHandler))
      }.map((response: GetQueryExecutionResult) => {
        val state = response.getQueryExecution.getStatus.getState
        logger.info(s"Query $id state: $state")
        state match {
          case Athena.queuedString => waitUntilQueryCompletes(id)
          case Athena.runningString => waitUntilQueryCompletes(id)
          case _ => Future.successful(())
        }
      }).flatten
    }

    val startQueryAndGetId: Future[String] = executeQuery(query).map((startQueryExecutionResponse: StartQueryExecutionResult) => startQueryExecutionResponse.getQueryExecutionId)
    startQueryAndGetId.flatMap((executionId: String) => waitUntilQueryCompletes(executionId).map(_ => executionId))
  }
  def fetchQueryResponse[T](id: String, transform: List[List[String]] => T)(implicit athenaAsync: AmazonAthenaAsync): Future[T] = {
    def readAndProcessNext(getQueryResultsResult: GetQueryResultsResult, last: List[List[String]] = Nil): Future[List[List[String]]] = {
      val next = last ++ getQueryResultsResult.getResultSet.getRows.asScala.toList.map(row => row.getData.asScala.map(_.getVarCharValue).toList)
      Option(getQueryResultsResult.getNextToken) match {
        case Some(token) => asyncHandle[GetQueryResultsRequest, GetQueryResultsResult](asyncHandler =>
          athenaAsync.getQueryResultsAsync(new GetQueryResultsRequest().withQueryExecutionId(id).withNextToken(token), asyncHandler)).flatMap(readAndProcessNext(_, next))
        case None => Future.successful(next)
      }
    }

    asyncHandle[GetQueryResultsRequest, GetQueryResultsResult](asyncHandler => athenaAsync.getQueryResultsAsync(new GetQueryResultsRequest().withQueryExecutionId(id), asyncHandler))
      .flatMap(readAndProcessNext(_)).map {
      case _ :: tail => tail
      case _ => Nil
    }.map(transform(_))
  }

}
