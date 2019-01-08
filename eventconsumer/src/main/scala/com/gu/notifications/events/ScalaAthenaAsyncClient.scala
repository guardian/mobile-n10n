package com.gu.notifications.events

import java.util.concurrent.ConcurrentLinkedQueue

import software.amazon.awssdk.services.athena.AthenaAsyncClient
import software.amazon.awssdk.services.athena.model.{GetQueryExecutionRequest, GetQueryExecutionResponse, GetQueryResultsRequest, GetQueryResultsResponse, Row, StartQueryExecutionRequest, StartQueryExecutionResponse}

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Future}

class ScalaAthenaAsyncClient(athenaAsyncClient: AthenaAsyncClient) {
  def startQueryExecution(request: StartQueryExecutionRequest): Future[StartQueryExecutionResponse] = FutureConverters.toScala(athenaAsyncClient.startQueryExecution(request))

  def getQueryExecution(request: GetQueryExecutionRequest): Future[GetQueryExecutionResponse] = FutureConverters.toScala(athenaAsyncClient.getQueryExecution(request))


  def fetchQueryResponse[T](id: String, transform: List[List[String]] => T)(implicit executionContext: ExecutionContext): Future[T] = {
    val queue = new ConcurrentLinkedQueue[List[List[String]]]()
    FutureConverters.toScala(athenaAsyncClient.getQueryResultsPaginator(GetQueryResultsRequest.builder()
      .queryExecutionId(id)
      .build()).subscribe((getQueryResultsResponse: GetQueryResultsResponse) => {
      val rows: List[Row] = getQueryResultsResponse.resultSet.rows().asScala.toList
      queue.add(rows.tail.map { row => row.data().asScala.toList.map(_.varCharValue()) })
    })).map(_ => transform(queue.asScala.flatten.toList))
  }
}
