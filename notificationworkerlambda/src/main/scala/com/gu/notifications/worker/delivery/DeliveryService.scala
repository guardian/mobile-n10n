package com.gu.notifications.worker.delivery

import java.util.concurrent.TimeUnit

import _root_.models.{Notification, ShardRange}
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

class DeliveryService[F[_], P <: DeliveryPayload, S <: DeliverySuccess, C <: DeliveryClient[P, S]]
  (registrationService: RegistrationService[F, Stream], client: C, maxConcurrency: Int = Int.MaxValue)
  (implicit ece: ExecutionContextExecutor, contextShift: Concurrent[F], F: Async[F], T: Timer[F]) {

  def send(
    notification: Notification,
    shardRange: ShardRange
  ): Stream[F, Either[DeliveryException, S]] = {

    def sendAsync(token: String, payload: P)(client: C): F[S] =
      Async[F].async[S] { (cb: Either[Throwable, S] => Unit) =>
        client.sendNotification(notification.id, token, payload)(cb)
      }

    val topicsF: F[NonEmptyList[Topic]] = notification
      .topic
      .map(t => Topic(t.fullName))
      .toNel
      .map(nel => F.delay(nel))
      .getOrElse(F.raiseError(InvalidTopics(notification.id)))

    def tokens: Stream[F, String] = for {
      topics <- Stream.eval(topicsF)
      res <- registrationService.findTokens(topics, Some(client.platform), Some(shardRange))
    } yield res

    def sending(token: String, payload: P, client: C): Stream[F, Either[DeliveryException, S]] = {

      val delayInMs = {
        val rangeInMs = Range(1000, 3000)
        rangeInMs.min + new Random().nextInt(rangeInMs.max - rangeInMs.min)
      }
      Stream
        .retry(
          sendAsync(token, payload)(client),
          delay = FiniteDuration(delayInMs, TimeUnit.MILLISECONDS),
          nextDelay = _.mul(2),
          maxAttempts = 3
        )
        .attempt
        .map {
          _.leftMap {
            _ match {
              case de: DeliveryException => de
              case NonFatal(e) => GenericFailure(notification.id, token, e)
            }
          }

        }
    }

    val payloadF: F[P] = client.payloadBuilder(notification)
      .fold[F[P]](F.raiseError(InvalidPayload(notification.id)))(p => F.delay(p))

    for {
      payload <- Stream.eval(payloadF)
      res <- tokens
        .map(token => sending(token, payload, client))
        .parJoin(maxConcurrency)
    } yield res
  }

  def close(): F[Unit] = F.delay(client.close())
}
