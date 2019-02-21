package com.gu.notifications.worker.tokens

import _root_.models.{Notification, Platform, ShardRange}
import cats.data.NonEmptyList
import cats.effect.{Async, Concurrent, Timer}
import cats.syntax.list._
import com.gu.notifications.worker.delivery.DeliveryException.InvalidTopics
import db.{RegistrationService, Topic}
import fs2.Stream
import play.api.libs.json.{Format, Json}

import scala.concurrent.ExecutionContextExecutor

case class IndividualNotification(notification: Notification, token: String, platform: Platform)

case class ChunkedTokens(notification: Notification, tokens: List[String], platform: Platform) {
  def toNotificationToSends: List[IndividualNotification] = tokens.map(IndividualNotification(notification, _, platform))
}

object ChunkedTokens {
  implicit val chunkTokensJf: Format[ChunkedTokens] = Json.format[ChunkedTokens]
}

trait TokenService[F[_]] {

  def batchTokens(
    notification: Notification,
    shardRange: ShardRange,
    platform: Platform
  ): Stream[F, ChunkedTokens]

}

class TokenServiceImpl[F[_]](
  registrationService: RegistrationService[F, Stream]
)(implicit ece: ExecutionContextExecutor,
  contextShift: Concurrent[F],
  F: Async[F],
  T: Timer[F]
) extends TokenService[F] {
  def tokens(
    notification: Notification,
    shardRange: ShardRange,
    platform: Platform
  ): Stream[F, String] = {

    val topicsF: F[NonEmptyList[Topic]] = notification
      .topic
      .map(t => Topic(t.fullName))
      .toNel
      .map(nel => F.delay(nel))
      .getOrElse(F.raiseError(InvalidTopics(notification.id)))

    for {
      topics <- Stream.eval(topicsF)
      res <- registrationService.findTokens(topics, Some(platform), Some(shardRange))
    } yield res
  }

  def batchTokens(
    notification: Notification,
    shardRange: ShardRange,
    platform: Platform
  ): Stream[F, ChunkedTokens] = {
    tokens(notification, shardRange, platform).chunkN(2).map(_.toList).map(ChunkedTokens(notification, _, platform))
  }
}
