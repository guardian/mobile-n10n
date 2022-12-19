package com.gu.notifications.worker.tokens

import _root_.models.{Notification, ShardRange}
import cats.data.NonEmptyList
import cats.effect.{Async, Concurrent, Timer}
import cats.syntax.list._
import com.gu.notifications.worker.delivery.DeliveryException.InvalidTopics
import db.{HarvestedToken, RegistrationService, Topic}
import fs2.Stream
import play.api.libs.json.{Format, Json}

import java.time.Instant
import scala.concurrent.ExecutionContextExecutor

case class IndividualNotification(notification: Notification, token: String)
case class BatchNotification(notification: Notification, token: List[String])

case class ChunkedTokens(notification: Notification, tokens: List[String], range: ShardRange, notificationAppReceivedTime: Option[Instant]) {
  def toNotificationToSends: List[IndividualNotification] = tokens.map(IndividualNotification(notification, _))
  def toBatchNotificationToSends: List[BatchNotification] = tokens.grouped(500).map(BatchNotification(notification, _)).toList
}

object ChunkedTokens {
  implicit val chunkTokensJf: Format[ChunkedTokens] = Json.format[ChunkedTokens]
}

trait TokenService[F[_]] {
  def tokens(
    notification: Notification,
    shardRange: ShardRange
  ): Stream[F, HarvestedToken]
}

class TokenServiceImpl[F[_]](
  registrationService: RegistrationService[F, Stream]
)(implicit ece: ExecutionContextExecutor,
  contextShift: Concurrent[F],
  F: Async[F],
  T: Timer[F]
) extends TokenService[F] {

  override def tokens(notification: Notification, shardRange: ShardRange): Stream[F, HarvestedToken] = {
    val topicsF: F[NonEmptyList[Topic]] = notification
      .topic
      .map(t => Topic(t.fullName))
      .toNel
      .map(nel => F.delay(nel))
      .getOrElse(F.raiseError(InvalidTopics(notification.id)))

    for {
      topics <- Stream.eval(topicsF)
      res <- registrationService.findTokens(topics, Some(shardRange))
    } yield res
  }
}
