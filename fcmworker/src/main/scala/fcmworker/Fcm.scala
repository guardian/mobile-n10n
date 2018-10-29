package fcmworker

import _root_.models.{Android, Notification, ShardRange}
import cats.data.NonEmptyList
import cats.effect.{Async, Concurrent}
import cats.syntax.either._
import cats.syntax.functor._
import cats.syntax.list._
import db.{RegistrationService, Topic}
import fcmworker.FcmClient.{FcmResponse, FcmSuccess, MessageId, Token}
import fcmworker.models.FcmException.{FcmGenericFailure, FcmInvalidPayload, FcmInvalidTopics}
import fcmworker.models.payload.FcmPayload
import fcmworker.models.{FcmConfig, FcmException}
import fs2.{Pipe, Stream}

import scala.concurrent.ExecutionContextExecutor

class Fcm[F[_]](registrationService: RegistrationService[F, Stream], config: FcmConfig)
  (implicit executionContext: ExecutionContextExecutor, concurrent: Concurrent[F], F: Async[F]) {

  private val fcmClientF: F[FcmClient] =
    FcmClient(config).fold(e => F.raiseError(e), c => F.delay(c))

  def send(notification: Notification, shardRange: ShardRange) = {

    def sendAsync(token: Token, payload: FcmPayload)(client: FcmClient): F[FcmSuccess] =
      Async[F].async[FcmSuccess] { (cb: FcmResponse => Unit) =>
        client.sendNotification(notification.id, token, payload)(cb)
      }

    val topicsF: F[NonEmptyList[Topic]] = notification
      .topic
      .map(t => Topic(t.name))
      .toNel
      .map(nel => F.delay(nel))
      .getOrElse(F.raiseError(FcmInvalidTopics(notification.id)))

    def tokens: Stream[F, Token] = for {
        topics <- Stream.eval(topicsF)
        res <- registrationService.findTokens(topics, Some(Android), Some(shardRange))
    } yield res

    def sending(token: Token, payload: FcmPayload, fcmClient: FcmClient): Stream[F, FcmResponse] = {

      def toFcmResponse: Pipe[F, FcmSuccess, FcmResponse] =
        _.attempt.map {
          _.leftMap {
            _ match {
              case fe: FcmException => fe
              case e: Throwable => FcmGenericFailure(notification.id, token, e)
            }
          }
        }

      Stream
        .eval(sendAsync(token, payload)(fcmClient))
        .through(toFcmResponse)
    }

    val payloadF = FcmPayload(notification, config.debug)
      .fold[F[FcmPayload]](F.raiseError(FcmInvalidPayload(notification.id)))(p => F.delay(p))

    val maxConcurrency = 500
    for {
      fcmClient <- Stream.eval(fcmClientF)
      payload <- Stream.eval(payloadF)
      res <- tokens
        .map(token => sending(token, payload, fcmClient))
        .parJoin(maxConcurrency)
    } yield res
  }

  def close(): F[Unit] = fcmClientF.map(_.close())
}
