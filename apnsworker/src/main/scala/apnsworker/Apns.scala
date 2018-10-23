package apnsworker

import cats.effect._
import cats.syntax.functor._
import com.turo.pushy.apns.{ApnsClient => PushyApnsClient}
import db.{RegistrationService, Shard, Topic}
import _root_.models.{Notification, iOS}
import fs2.Stream
import models.ApnsConfig

import scala.concurrent.{ExecutionContext, Future, blocking}

class Apns[F[_]](registrationService: RegistrationService[F, Stream], config: ApnsConfig)
  (implicit executionContext: ExecutionContext, contextShift: Concurrent[F], F: Async[F]) {

  private val apnsClient: F[PushyApnsClient] =
    ApnsClient(config).fold(e => F.raiseError(e), c => F.delay(c))

  def send(notification: Notification, shards: Seq[Shard]): Stream[F, Either[Throwable, String]] = {

    def sendAsync(token: String)(client: PushyApnsClient): F[String] =
      Async[F].async[String] { (cb: Either[Throwable, String] => Unit) =>
        ApnsClient.sendNotification(token, notification)(cb)(client, config)
      }

    def tokens: Stream[F, String] = notification
        .topic
        .map(topic => registrationService.find(Topic(topic.name), iOS, shards:_*))
        .reduce(_ merge _)
        .map(_.device.token)

    for {
      client <- Stream.eval(apnsClient)
      res <- tokens
        .map(token => Stream.eval(sendAsync(token)(client)).attempt)
        .parJoinUnbounded
    } yield res
  }

  def close(): F[Future[Unit]] =
    apnsClient.map(client => Future(blocking(client.close().get())))
}
