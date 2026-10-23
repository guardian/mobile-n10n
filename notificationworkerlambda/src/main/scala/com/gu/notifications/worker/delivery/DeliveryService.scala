package com.gu.notifications.worker.delivery

import _root_.models.Notification
import cats.effect._
import cats.syntax.either._
import com.gu.notifications.worker.delivery.DeliveryException.{FailedAPNSDelivery, GenericFailure, InvalidPayload, InvalidToken}
import com.gu.notifications.worker.utils.Logging
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
) extends DeliveryService[F, C] with Logging {
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def send(
    notification: Notification,
    token: String
  ): Stream[F, Either[DeliveryException, C#Success]] = {

    def sendAsync(client: C)(token: String, payload: client.Payload): F[C#Success] =
      Async[F].async { (cb: Either[Throwable, C#Success] => Unit) =>
        client.sendNotification(
          notification.id,
          token,
          payload,
          notification.dryRun.contains(true) || client.dryRun
        )(cb)
      }

    def sending(client: C)(token: String, payload: client.Payload): Stream[F, Either[DeliveryException, C#Success]] = {

      val delayInMs = {
        val rangeInMs = Range(1000, 3000)
        rangeInMs.min + Random.nextInt(rangeInMs.length)
      }

      // Only APNS delivery failures are retried - todo
      // No FCM or other failures are retried, as DeliveryException extends a Throwable which is not caught by the NonFatal Exception pattern match
      Stream
        .retry(
          sendAsync(client)(token, payload),
          delay = FiniteDuration(delayInMs, TimeUnit.MILLISECONDS),
          nextDelay = _.mul(2),
          maxAttempts = 3,
          retriable = {
            case NonFatal(e: FailedAPNSDelivery) =>
              logger.info(Map(
                "notificationId" -> notification.id,
                "apns.rejectionReason" -> e.reason,
                "token" -> token
              ), s"Failed delivery, will retry")
              true
            case NonFatal(e: InvalidToken) => false
            case NonFatal(exception: Exception) =>
              logger.error(Map(
                "notificationId" -> notification.id,
                "token" -> token
              ), "Encountered an error, will retry", exception)
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
