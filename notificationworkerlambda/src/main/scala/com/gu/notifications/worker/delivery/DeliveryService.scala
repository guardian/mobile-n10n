package com.gu.notifications.worker.delivery

import java.util.concurrent.TimeUnit

import _root_.models.{Notification, Platform}
import cats.effect._
import cats.syntax.either._
import com.gu.notifications.worker.delivery.DeliveryException.{GenericFailure, InvalidPayload}
import fs2.Stream

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.util.Random
import scala.util.control.NonFatal

trait DeliveryService[F[_], C <: DeliveryClient] {
  def send(
    notification: Notification,
    token: String,
    platform: Platform
  ): Stream[F, Either[DeliveryException, C#Success]]
}

class DeliveryServiceImpl[F[_], C <: DeliveryClient] (
  client: C
)(implicit ece: ExecutionContextExecutor,
  contextShift: Concurrent[F],
  F: Async[F],
  T: Timer[F]
) extends DeliveryService[F, C] {

  def send(
    notification: Notification,
    token: String,
    platform: Platform
  ): Stream[F, Either[DeliveryException, C#Success]] = {

    def sendAsync(client: C)(token: String, payload: client.Payload): F[C#Success] =
      Async[F].async { (cb: Either[Throwable, C#Success] => Unit) =>
        client.sendNotification(
          notification.id,
          token,
          payload,
          platform
        )(cb)
      }

    def sending(client: C)(token: String, payload: client.Payload): Stream[F, Either[DeliveryException, C#Success]] = {

      val delayInMs = {
        val rangeInMs = Range(1000, 3000)
        rangeInMs.min + Random.nextInt(rangeInMs.length)
      }
      Stream
        .retry(
          sendAsync(client)(token, payload),
          delay = FiniteDuration(delayInMs, TimeUnit.MILLISECONDS),
          nextDelay = _.mul(2),
          maxAttempts = 3
        )
        .attempt
        .map {
          _.leftMap {
            case de: DeliveryException => de
            case NonFatal(e) => GenericFailure(notification.id, token, e)
          }
        }
        .map( x => {
          println(x)
          x
        })
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
