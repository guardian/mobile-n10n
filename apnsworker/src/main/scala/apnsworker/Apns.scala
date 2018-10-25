package apnsworker

import cats.effect._
import cats.syntax.functor._
import com.turo.pushy.apns.{ApnsClient => PushyApnsClient}
import db.{RegistrationService, ShardRange, Topic}
import _root_.models.{Notification, iOS}
import apnsworker.payload.ApnsPayload
import fs2.Stream
import models.ApnsConfig

import scala.concurrent.{ExecutionContext, Future, blocking}

class Apns[F[_]](registrationService: RegistrationService[F, Stream], config: ApnsConfig)
  (implicit executionContext: ExecutionContext, contextShift: Concurrent[F], F: Async[F]) {

  private val apnsClientF: F[PushyApnsClient] =
    ApnsClient(config).fold(e => F.raiseError(e), c => F.delay(c))

  def send(notification: Notification, shardRange: ShardRange): Stream[F, Either[Throwable, String]] = {

    def sendAsync(token: String, payload: ApnsPayload)(client: PushyApnsClient): F[String] =
      Async[F].async[String] { (cb: Either[Throwable, String] => Unit) =>
        ApnsClient.sendNotification(notification.id, token, payload)(cb)(client, config)
      }

    def tokens: Stream[F, String] = notification
        .topic
        .map(topic => registrationService.find(Topic(topic.name), iOS, shardRange))
        .reduce(_ merge _)
        .map(_.device.token)
    //TODO: de-duplicate

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
