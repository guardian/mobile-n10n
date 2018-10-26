package db

import cats.effect.Async
import doobie.implicits._
import doobie.util.transactor.Transactor
import fs2.Stream
import Registration._
import cats.data.NonEmptyList
import doobie.free.connection.ConnectionIO
import doobie.postgres.sqlstate
import doobie.Fragments
import models.PlatformCount

class SqlRegistrationRepository[F[_]: Async](xa: Transactor[F])
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


  override def findByToken(token: String): Stream[F, Registration] = {
    sql"""
         SELECT token, platform, topic, shard, lastModified
         FROM registrations
         WHERE token = $token
      """
      .query[Registration]
      .stream
      .transact(xa)
  }

  override def save(reg: Registration): F[Int] =
    // save = upsert (trying to insert first, if unique violation then update)
    insert(reg).exceptSomeSqlState {
      case sqlstate.class23.UNIQUE_VIOLATION => update(reg)
    }.transact(xa)

  override def remove(reg: Registration): F[Int] = sql"""
        DELETE FROM registrations WHERE token = ${reg.device.token} AND topic = ${reg.topic.name}
      """
      .update.run.transact(xa)


  override def removeByToken(token: String): F[Int] = {
    sql"""
      DELETE FROM registrations WHERE token = $token
    """
    .update.run.transact(xa)
  }

  private def insert(reg: Registration): ConnectionIO[Int] =
    sql"""
        INSERT INTO registrations (token, platform, topic, shard, lastModified)
        VALUES (
          ${reg.device.token},
          ${reg.device.platform},
          ${reg.topic.name},
          ${reg.shard.id},
          CURRENT_TIMESTAMP
        )
      """
    .update.run

  private def update(reg: Registration): ConnectionIO[Int] =
    sql"""
        UPDATE registrations
        SET lastModified = CURRENT_TIMESTAMP, shard = ${reg.shard.id}
        WHERE token = ${reg.device.token} AND topic = ${reg.topic.name}
      """
      .update.run

  def countPerPlatformForTopics(topics: NonEmptyList[Topic]): F[PlatformCount] = {
    val q = fr"""
      SELECT
        count(1) as total,
        coalesce(sum(case platform when 'ios' then 1 else 0 end), 0) as ios,
        coalesce(sum(case platform when 'android' then 1 else 0 end), 0) as android,
        coalesce(sum(case platform when 'newsstand' then 1 else 0 end), 0) as newsstand
      FROM
        (
          SELECT DISTINCT
            token,
            platform
          FROM
            registrations
          WHERE
      """ ++
        Fragments.in(fr"topic", topics) ++
    fr"""
        ) as distinct_registrations
    """

    q.query[PlatformCount]
      .unique
      .transact(xa)
  }
}
