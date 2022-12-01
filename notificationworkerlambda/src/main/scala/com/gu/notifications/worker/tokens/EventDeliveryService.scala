package com.gu.notifications.worker.tokens

import java.util.concurrent.TimeUnit

import cats.effect._
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.model.{SendMessageRequest, SendMessageResult}
import com.gu.notifications.worker.utils.Aws
import fs2.Stream
import play.api.libs.json.Json

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.util.Random
import models.Platform
import com.amazonaws.services.eventbridge.AmazonEventBridgeClient
import com.amazonaws.services.eventbridge.model.PutEventsRequestEntry
import java.util.Date
import com.amazonaws.services.eventbridge.model.PutEventsRequest
import com.amazonaws.services.eventbridge.model.PutEventsResult
import com.amazonaws.services.eventbridge.AmazonEventBridgeAsyncClient

trait EventDeliveryService[F[_]] {
  def streamEmit(sqsCalls: Stream[F, Either[Throwable, Unit]]): Stream[F, Either[Throwable, Unit]]
}

class EventDeliveryServiceImpl[F[_]](platform: Platform, queueUrl: String)(implicit ece: ExecutionContextExecutor,
  contextShift: Concurrent[F],
  F: Async[F],
  T: Timer[F]
) extends EventDeliveryService[F] {
  val eventBridgeAsyncClient = AmazonEventBridgeAsyncClient.asyncBuilder()
    .withCredentials(Aws.credentialsProvider)
    .withRegion(Regions.EU_WEST_1)
    .build()

  def emit()(oncomplete: Either[Throwable, Unit] => Unit): Unit = {
    val requestEntry = new PutEventsRequestEntry()
        .withTime(new Date())
        .withSource("com.gu.notifications.worker.Harvester")
        .withDetailType("Tokens sent")
        .withResources(queueUrl)
        .withDetail(s"{ \"platform\": \"${platform.toString()}\" }");

    val request = new PutEventsRequest()
        .withEntries(requestEntry);

    val handler = new AsyncHandler[PutEventsRequest, PutEventsResult] {
      override def onError(exception: Exception): Unit = oncomplete(Left(new Exception(s"Failed to emit events to EventBridge for ${platform.toString()}", exception)))

      override def onSuccess(request: PutEventsRequest, result: PutEventsResult): Unit = oncomplete(Right(()))
    }
    eventBridgeAsyncClient.putEventsAsync(request, handler);
  }

  private def emitAsync(): F[Unit] =
    Async[F].async { (cb: Either[Throwable, Unit] => Unit) =>
      emit()(cb)
    }

  private def emitting(sqsResult: Either[Throwable, Unit]): Stream[F, Either[Throwable, Unit]] = {
    sqsResult match {
      case Right(_) => {
        val delayInMs = {
          val rangeInMs = Range(1000, 3000)
          rangeInMs.min + Random.nextInt(rangeInMs.length)
        }
        Stream
          .retry(
            emitAsync(),
            delay = FiniteDuration(delayInMs, TimeUnit.MILLISECONDS),
            nextDelay = _.mul(2),
            maxAttempts = 3
          )
          .attempt
      }
      case Left(_) => Stream.emit(sqsResult)
    }
  }
  

  override def streamEmit(sqsCalls: Stream[F, Either[Throwable, Unit]]): Stream[F, Either[Throwable, Unit]] = 
    sqsCalls.flatMap(emitting(_))


}
