package com.gu.notifications.worker.tokens

import java.util.concurrent.TimeUnit

import cats.effect._
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.services.sqs.model.{SendMessageRequest, SendMessageResult}
import com.gu.notifications.worker.utils.Aws
import fs2.Stream
import play.api.libs.json.Json

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

trait SqsDeliveryService[F[_]]{
  def sending(chunkedTokens: ChunkedTokens): Stream[F, Either[Throwable, Unit]]
}
class SqsDeliveryServiceImpl[F[_]](queueUrl: String)(implicit ece: ExecutionContextExecutor,
  contextShift: Concurrent[F],
  F: Async[F],
  T: Timer[F]
) extends SqsDeliveryService[F]{
  val amazonSQSAsyncClient = AmazonSQSAsyncClientBuilder.standard()
    .withCredentials(Aws.credentialsProvider)
    .withRegion(Regions.EU_WEST_1)
    .build()


  private def send(chunkedTokens: ChunkedTokens)(oncomplete: Either[Exception, Unit] => Unit): Unit = {
    amazonSQSAsyncClient.sendMessageAsync(new SendMessageRequest()
      .withMessageBody(Json.stringify(Json.toJson(chunkedTokens)))
        .withQueueUrl(queueUrl)
      , new AsyncHandler[SendMessageRequest, SendMessageResult] {
        override def onError(exception: Exception): Unit = oncomplete(Left(new Exception(Json.stringify(Json.toJson(chunkedTokens)), exception)))

        override def onSuccess(request: SendMessageRequest, result: SendMessageResult): Unit = oncomplete(Right(()))
      })
  }


  private def sendAsync(chunkedTokens: ChunkedTokens): F[Unit] =
    Async[F].async { (cb: Either[Throwable, Unit] => Unit) =>
      send(chunkedTokens)(cb)
    }


  override def sending(chunkedTokens: ChunkedTokens): Stream[F, Either[Throwable, Unit]] = {
    val delayInMs = {
      val rangeInMs = Range(1000, 3000)
      rangeInMs.min + Random.nextInt(rangeInMs.length)
    }
    Stream
      .retry(
        sendAsync(chunkedTokens),
        delay = FiniteDuration(delayInMs, TimeUnit.MILLISECONDS),
        nextDelay = _.mul(2),
        maxAttempts = 3
      )
      .attempt

  }

}
