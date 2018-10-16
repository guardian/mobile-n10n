package db

import cats.effect.{Async, Sync}
import doobie.implicits._
import doobie.util.transactor.Transactor
import fs2.Stream
import Registration._

class SqlRegistrationsRepository[F[_]: Async](xa: Transactor[F])(implicit F: Sync[F])
  extends RegistrationRepository[F, Stream] {

  override def findByTopic(topic: Topic): Stream[F, Registration] =
    sql"""
         SELECT token, platform, topic, shard, lastModified
         FROM registrations
         WHERE topic = ${topic.name}
      """
      .query[Registration]
      .stream
      .transact(xa)

  override def save(reg: Registration): F[Int] =
    sql"""
        INSERT INTO registrations (token, platform, topic, shard, lastModified)
        VALUES (
          ${reg.device.token},
          ${reg.device.platform},
          ${reg.topic.name},
          ${reg.shard.id},
          ${reg.lastModified}
        )
      """
      .update.run.transact(xa)

  override def remove(reg: Registration): F[Int] = sql"""
        DELETE FROM registrations WHERE token = ${reg.device.token} AND topic = ${reg.topic.name}
      """
      .update.run.transact(xa)

}
