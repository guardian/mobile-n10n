package com.gu.notifications.events

import software.amazon.awssdk.services.athena.AthenaAsyncClient
import software.amazon.awssdk.services.athena.model.{GetQueryExecutionRequest, GetQueryExecutionResponse, GetQueryResultsRequest, GetQueryResultsResponse, StartQueryExecutionRequest, StartQueryExecutionResponse}

import scala.compat.java8.FutureConverters
import scala.concurrent.Future

class ScalaAthenaAsyncClient(athenaAsyncClient: AthenaAsyncClient) {
  def startQueryExecution(request: StartQueryExecutionRequest): Future[StartQueryExecutionResponse] = FutureConverters.toScala(athenaAsyncClient.startQueryExecution(request))

  def getQueryExecution(request: GetQueryExecutionRequest): Future[GetQueryExecutionResponse] = FutureConverters.toScala(athenaAsyncClient.getQueryExecution(request))

  def subscribeToPagination[T](id: String, consumer: GetQueryResultsResponse => T): Future[Void] = {
    FutureConverters.toScala(athenaAsyncClient.getQueryResultsPaginator(GetQueryResultsRequest.builder()
      .queryExecutionId(id)
      .build()).subscribe(x => consumer(x)))
  }
}
