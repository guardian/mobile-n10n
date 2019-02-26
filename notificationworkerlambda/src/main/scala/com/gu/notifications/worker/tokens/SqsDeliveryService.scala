package com.gu.notifications.worker.tokens

import java.util.concurrent.TimeUnit

import cats.effect._
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.services.sqs.model.{SendMessageBatchRequest, SendMessageBatchRequestEntry, SendMessageBatchResult}
import com.gu.notifications.worker.utils.Aws
import fs2.Stream
import play.api.libs.json.Json

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

trait SqsDeliveryService[F[_]] {
  def sending(chunkedTokens: List[ChunkedTokens]): Stream[F, Either[Throwable, Unit]]
}

class SqsDeliveryServiceImpl[F[_]](queueUrl: String)(implicit ece: ExecutionContextExecutor,
  contextShift: Concurrent[F],
  F: Async[F],
  T: Timer[F]
) extends SqsDeliveryService[F] {
  val amazonSQSAsyncClient = AmazonSQSAsyncClientBuilder.standard()
    .withCredentials(Aws.credentialsProvider)
    .withRegion(Regions.EU_WEST_1)
    .build()


  def sendBatch(chunkedTokensBatch: List[ChunkedTokens])(oncomplete: Either[Throwable, Unit] => Unit): Unit = {
    val sendMessageBatchRequest = new SendMessageBatchRequest()
      .withEntries(chunkedTokensBatch.zipWithIndex.map { case (chunkedTokens, index) => {
        val json = Json.stringify(Json.toJson(chunkedTokens))
        new SendMessageBatchRequestEntry()
          .withMessageBody(json)
          .withId(index.toString)
      }
      }.asJava)
      .withQueueUrl(queueUrl)
    val handler = new AsyncHandler[SendMessageBatchRequest, SendMessageBatchResult] {
      override def onError(exception: Exception): Unit = oncomplete(Left(new Exception(Json.stringify(Json.toJson(chunkedTokensBatch)), exception)))

      override def onSuccess(request: SendMessageBatchRequest, result: SendMessageBatchResult): Unit =
        result.getFailed.asScala.toList match {
          case Nil => oncomplete(Right(()))
          case failures => {
            oncomplete(Left(new Exception(failures.map(failure => s"${failure.getMessage}: ${chunkedTokensBatch(failure.getId.toInt)}").mkString("\n"))))
          }
        }
    }
    amazonSQSAsyncClient.sendMessageBatchAsync(
      sendMessageBatchRequest,
      handler)
  }


  private def sendAsync(chunkedTokensBatch: List[ChunkedTokens]): F[Unit] =
    Async[F].async { (cb: Either[Throwable, Unit] => Unit) =>
      sendBatch(chunkedTokensBatch)(cb)
    }


  override def sending(chunkedTokensBatch: List[ChunkedTokens]): Stream[F, Either[Throwable, Unit]] = {
    val delayInMs = {
      val rangeInMs = Range(1000, 3000)
      rangeInMs.min + Random.nextInt(rangeInMs.length)
    }
    Stream
      .retry(
        sendAsync(chunkedTokensBatch),
        delay = FiniteDuration(delayInMs, TimeUnit.MILLISECONDS),
        nextDelay = _.mul(2),
        maxAttempts = 3
      )
      .attempt

  }

}
