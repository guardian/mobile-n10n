package com.gu.notifications.worker.delivery

import java.util.concurrent.TimeUnit

import _root_.models.{Notification, ShardRange, Platform}
import cats.data.NonEmptyList
import cats.effect._
import cats.syntax.either._
import cats.syntax.list._
import com.gu.notifications.worker.delivery.DeliveryException.{GenericFailure, InvalidPayload, InvalidTopics}
import db.{RegistrationService, Topic}
import fs2.Stream

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.util.Random
import scala.util.control.NonFatal

trait DeliveryService[F[_], C <: DeliveryClient] {
  def send(
    notification: Notification,
    shardRange: ShardRange,
    platform: Platform
  ): Stream[F, Either[DeliveryException, C#Success]]
}

class DeliveryServiceImpl[F[_], C <: DeliveryClient] (
  registrationService: RegistrationService[F, Stream],
  client: C,
  maxConcurrency: Int = Int.MaxValue
)(implicit ece: ExecutionContextExecutor,
  contextShift: Concurrent[F],
  F: Async[F],
  T: Timer[F]
) extends DeliveryService[F, C] {

  def send(
    notification: Notification,
    shardRange: ShardRange,
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

    val topicsF: F[NonEmptyList[Topic]] = notification
      .topic
      .map(t => Topic(t.fullName))
      .toNel
      .map(nel => F.delay(nel))
      .getOrElse(F.raiseError(InvalidTopics(notification.id)))

    def tokens: Stream[F, String] = for {
      topics <- Stream.eval(topicsF)
      res <- registrationService.findTokens(topics, Some(platform), Some(shardRange))
    } yield res

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
    }

    val payloadF: F[client.Payload] = client
      .payloadBuilder(notification)
      .map(p => F.delay(p))
      .getOrElse(F.raiseError(InvalidPayload(notification.id)))

    for {
      payload <- Stream.eval(payloadF)
      res <- tokens
        .map(token => sending(client)(token, payload))
        .parJoin(maxConcurrency)
    } yield res
  }

  def close(): F[Unit] = F.delay(client.close())
}
