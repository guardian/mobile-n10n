package com.gu.notifications.worker.delivery

import _root_.models.Notification
import cats.effect.{Async, Concurrent, Timer}
import cats.syntax.either._
import com.gu.notifications.worker.delivery.DeliveryException.{FailedAPNSDelivery, FailedAPNSRequest, GenericFailure, InvalidPayload, InvalidToken}
import fs2.Stream
import org.slf4j.{Logger, LoggerFactory}

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.util.Random
import scala.util.control.NonFatal

trait DeliveryService[F[_], C <: DeliveryClient] {
  def send(
    notification: Notification,
    token: String
  ): Stream[F, Either[DeliveryException, C#Success]]
}

class DeliveryServiceImpl[F[_], C <: DeliveryClient] (
  client: C
)(implicit ece: ExecutionContextExecutor,
  contextShift: Concurrent[F],
  F: Async[F],
  T: Timer[F]
) extends DeliveryService[F, C] {
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def send(
    notification: Notification,
    token: String
  ): Stream[F, Either[DeliveryException, C#Success]] = {

    def sendAsync(client: C)(token: String, payload: client.Payload): F[C#Success] = {
      // Boundary between completableFuture and callback that must return Unit.
      // cb is manufactured by cats.effect.Async.async, and is a callback that must return Unit.
      // TODO migrate to cats.effect v3 for Async.fromCompletableFuture but will require wider changes and dependency upgrades.
      Async[F].async { (cb: Either[Throwable, C#Success] => Unit) =>
        client.sendNotification(
          notification.id,
          token,
          payload,
          notification.dryRun.contains(true) || client.dryRun
        ).whenComplete((result: Either[DeliveryException, C#Success], error: Throwable) =>
          if (error != null) cb(Left(error)) else cb(result)
        )
        () // explicitly discard the returned CompletableFuture, yield Unit
      }
    }

    def sending(client: C)(token: String, payload: client.Payload): Stream[F, Either[DeliveryException, C#Success]] = {

      val delayInMs = {
        val rangeInMs = Range(1000, 3000)
        rangeInMs.min + Random.nextInt(rangeInMs.length)
      }

      val retriableApnsCauses = List(
        "Stream closed before write could take place"
      )

      def hasRetriableCause(e: FailedAPNSRequest): Boolean =
        Option(e.cause)
          .flatMap(c => Option(c.getMessage))
          .exists(msg => retriableApnsCauses.exists(msg.contains))

      Stream
        .retry(
          sendAsync(client)(token, payload),
          delay = FiniteDuration(delayInMs, TimeUnit.MILLISECONDS),
          nextDelay = _.mul(2),
          maxAttempts = 3,
          retriable = {
            case NonFatal(e: FailedAPNSDelivery) =>
              logger.info(s"Retrying failed APNS delivery: ${e.getMessage}", e)

              true
            case NonFatal(e: FailedAPNSRequest) if hasRetriableCause(e) =>
              logger.info(s"Retrying failed APNS request: ${e.getMessage}", e)
              true
            case NonFatal(e: FailedAPNSRequest) => false
            case NonFatal(e: InvalidToken) => false
            case NonFatal(exception: Exception) =>
              logger.error("Encountered an error, will retry", exception)
              true
            case _ => false
          }
        )
        .attempt
        .map {
          _.leftMap {
            case de: DeliveryException => de
            case NonFatal(e) => GenericFailure(notification.id, token, e)
          }
        }
    }

    val payloadF: F[client.Payload] = client
      .payloadBuilder(notification)
      .map(p => F.delay(p))
      .getOrElse(F.raiseError(InvalidPayload(notification.id)))

    for {
      payload <- Stream.eval(payloadF)
      res <- sending(client)(token, payload)
    } yield res
  }
}
