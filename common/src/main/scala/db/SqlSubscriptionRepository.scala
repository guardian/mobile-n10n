package db

import cats.effect.{Async, Sync}
import doobie.implicits._
import doobie.util.transactor.Transactor
import fs2.Stream
import Subscription._

class SqlSubscriptionRepository[F[_]: Async](xa: Transactor[F])(implicit F: Sync[F])
  extends SubscriptionRepository[F, Stream] {

  private val table = "subscriptions"

  override def findByTopic(topic: Topic): Stream[F, Subscription] =
    sql"""
         SELECT token, platform, topic, shard, last_modified
         FROM subscriptions
         WHERE topic = ${topic.name}
      """
      .query[Subscription]
      .stream
      .transact(xa)

  override def save(sub: Subscription): F[Int] =
    sql"""
        INSERT INTO subscriptions (token, platform, topic, shard, last_modified)
        VALUES (
          ${sub.device.token},
          ${sub.device.platform},
          ${sub.topic.name},
          ${sub.shard.id},
          ${sub.lastModified}
        )
      """
      .update.run.transact(xa)

  override def remove(sub: Subscription): F[Int] = sql"""
        DELETE FROM subscriptions WHERE token = ${sub.device.token} AND topic = ${sub.topic.name}
      """
      .update.run.transact(xa)

}
