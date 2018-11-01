package com.gu.notifications.worker.delivery

import _root_.models.{Notification, ShardRange}
import cats.data.NonEmptyList
import cats.effect._
import cats.syntax.either._
import cats.syntax.list._
import com.gu.notifications.worker.delivery.DeliveryException.{GenericFailure, InvalidPayload, InvalidTopics}
import db.{RegistrationService, Topic}
import fs2.{Pipe, Stream}

import scala.concurrent.{ExecutionContextExecutor}
import scala.util.control.NonFatal

class DeliveryService[F[_], P <: DeliveryPayload, S <: DeliverySuccess, C <: DeliveryClient[P, S]]
  (registrationService: RegistrationService[F, Stream], client: C, maxConcurrency: Int = Int.MaxValue)
  (implicit ece: ExecutionContextExecutor, contextShift: Concurrent[F], F: Async[F]) {

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

      def toDeliveryResponse(token: String): Pipe[F, S, Either[DeliveryException, S]] =
        _.attempt.map {
          _.leftMap {
            _ match {
              case de: DeliveryException => de
              case NonFatal(e) => GenericFailure(notification.id, token, e)
            }
          }
        }

      Stream
        .eval(sendAsync(token, payload)(client))
        .through(toDeliveryResponse(token))
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
