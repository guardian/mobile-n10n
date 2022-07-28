package com.gu.notifications.workerlambda.tokens

import java.util.concurrent.TimeUnit
import cats.effect._
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import com.gu.notifications.workerlambda.utils.Aws
import fs2.Stream
import play.api.libs.json.Json

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Random, Success, Try}

trait SqsDeliveryService[F[_]] {
  def sending(chunkedTokens: ChunkedTokens): Stream[F, Either[Throwable, Unit]]
}

class SqsDeliveryServiceImpl[F[_]](queueUrl: String)(implicit ece: ExecutionContextExecutor,
  contextShift: Concurrent[F],
  F: Async[F],
  T: Timer[F]
) extends SqsDeliveryService[F] {
  val amazonSQSClient = SqsClient.builder()
    .credentialsProvider(Aws.CredentialsProvider)
    .region(Region.EU_WEST_1)
    .build()

  def send(chunkedTokensBatch: ChunkedTokens)(oncomplete: Either[Throwable, Unit] => Unit): Unit = {
    val sendMessageRequest = SendMessageRequest.builder()
      .messageBody(Json.stringify(Json.toJson(chunkedTokensBatch)))
      .queueUrl(queueUrl)
      .build()

    val response = Try(amazonSQSClient.sendMessage(sendMessageRequest))
    response match {
      case Success(result) =>
        oncomplete(Right(()))
      case Failure(throwable) =>
        oncomplete(Left(new Exception(Json.stringify(Json.toJson(chunkedTokensBatch)), throwable)))
    }
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
