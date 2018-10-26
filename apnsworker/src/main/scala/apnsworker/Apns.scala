package apnsworker

import cats.effect._
import cats.syntax.functor._
import cats.syntax.list._
import cats.syntax.either._
import com.turo.pushy.apns.{ApnsClient => PushyApnsClient}
import db.{RegistrationService, Topic}
import _root_.models.{Notification, ShardRange, iOS}
import apnsworker.ApnsClient.{ApnsResponse, Token}
import apnsworker.models.ApnsException.{ApnsGenericFailure, ApnsInvalidPayload, ApnsInvalidTopics}
import apnsworker.models.payload.ApnsPayload
import cats.data.NonEmptyList
import fs2.{Pipe, Stream}
import models.{ApnsConfig, ApnsException}

import scala.concurrent.{ExecutionContext, Future, blocking}

class Apns[F[_]](registrationService: RegistrationService[F, Stream], config: ApnsConfig)
  (implicit executionContext: ExecutionContext, contextShift: Concurrent[F], F: Async[F]) {

  private val apnsClientF: F[PushyApnsClient] =
    ApnsClient(config).fold(e => F.raiseError(e), c => F.delay(c))

  def send(notification: Notification, shardRange: ShardRange): Stream[F, ApnsResponse] = {

    def sendAsync(token: Token, payload: ApnsPayload)(client: PushyApnsClient): F[Token] =
      Async[F].async[Token] { (cb: ApnsResponse => Unit) =>
        ApnsClient.sendNotification(notification.id, token, payload)(cb)(client, config)
      }

    val topicsF: F[NonEmptyList[Topic]] = notification
      .topic
      .map(t => Topic(t.name))
      .toNel
      .map(nel => F.delay(nel))
      .getOrElse(F.raiseError(ApnsInvalidTopics(notification.id)))

    def tokens: Stream[F, Token] = for {
        topics <- Stream.eval(topicsF)
        res <- registrationService.findTokens(topics, Some(iOS), Some(shardRange))
    } yield res

    def sending(token: Token, payload: ApnsPayload, apnsClient: PushyApnsClient): Stream[F, ApnsResponse] = {

      def toApnsResponse(token: Token): Pipe[F, Token, ApnsResponse] =
        _.attempt.map {
          _.leftMap {
            _ match {
              case ae: ApnsException => ae
              case e: Throwable => ApnsGenericFailure(notification.id, token, e)
            }
          }
        }

      Stream
        .eval(sendAsync(token, payload)(apnsClient))
        .through(toApnsResponse(token))
    }

    val payloadF = ApnsPayload(notification)
      .fold[F[ApnsPayload]](F.raiseError(ApnsInvalidPayload(notification.id)))(p => F.delay(p))

    for {
      apnsClient <- Stream.eval(apnsClientF)
      payload <- Stream.eval(payloadF)
      res <- tokens
        .map(token => sending(token, payload, apnsClient))
        .parJoinUnbounded
    } yield res
  }

  def close(): F[Future[Unit]] =
    apnsClientF.map(client => Future(blocking(client.close().get())))
}
