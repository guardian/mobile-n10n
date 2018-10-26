package apnsworker

import cats.effect._
import cats.syntax.functor._
import cats.syntax.list._
import com.turo.pushy.apns.{ApnsClient => PushyApnsClient}
import db.{RegistrationService, Topic}
import _root_.models.{Notification, ShardRange, iOS}
import apnsworker.ApnsClient.{ApnsResponse, Token}
import apnsworker.models.payload.ApnsPayload
import cats.data.NonEmptyList
import fs2.Stream
import models.ApnsConfig

import scala.concurrent.{ExecutionContext, Future, blocking}

class Apns[F[_]](registrationService: RegistrationService[F, Stream], config: ApnsConfig)
  (implicit executionContext: ExecutionContext, contextShift: Concurrent[F], F: Async[F]) {

  private val apnsClientF: F[PushyApnsClient] =
    ApnsClient(config).fold(e => F.raiseError(e), c => F.delay(c))

  def send(notification: Notification, shardRange: ShardRange): Stream[F, Either[Throwable, Token]] = {

    def sendAsync(token: String, payload: ApnsPayload)(client: PushyApnsClient): F[String] =
      Async[F].async[String] { (cb: ApnsResponse => Unit) =>
        ApnsClient.sendNotification(notification.id, token, payload)(cb)(client, config)
      }

    val topicsF: F[NonEmptyList[Topic]] = notification
      .topic
      .map(t => Topic(t.name))
      .toNel
      .map(nel => F.delay(nel))
      .getOrElse(F.raiseError(new RuntimeException(s"Error: No topic for notification $notification")))

    val tokens: Stream[F, String] = for {
        topics <- Stream.eval(topicsF)
        res <- registrationService.findTokens(topics, Some(iOS), Some(shardRange))
    } yield res

    val payloadF = ApnsPayload(notification)
      .fold[F[ApnsPayload]](F.raiseError(new RuntimeException(s"Error: Cannot generate payload for notification $notification")))(p => F.delay(p))

    for {
      apnsClient <- Stream.eval(apnsClientF)
      payload <- Stream.eval(payloadF)
      res <- tokens
        .map(token => Stream.eval(sendAsync(token, payload)(apnsClient)).attempt)
        .parJoinUnbounded
    } yield res
  }

  def close(): F[Future[Unit]] =
    apnsClientF.map(client => Future(blocking(client.close().get())))
}
