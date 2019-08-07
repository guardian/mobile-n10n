package db

import cats.effect.Async
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import fs2.Stream
import Registration._
import cats.data.NonEmptyList
import doobie.free.connection.ConnectionIO
import doobie.postgres.sqlstate
import doobie.Fragments
import models.{Platform, TopicCount}
import play.api.Logger

class SqlRegistrationRepository[F[_]: Async](xa: Transactor[F])
  extends RegistrationRepository[F, Stream] {
  val logger = Logger(classOf[SqlRegistrationRepository[F]])

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

  override def topicCounts(countThreshold: Int): Stream[F, TopicCount] = {
    sql"""
         SELECT topic, count(topic) as topic_count from registrations
         GROUP BY topic
         HAVING count(topic) > $countThreshold
         ORDER BY topic_count desc
      """
        .query[TopicCount]
        .stream
        .transact(xa)
  }

  override def findTokens(topics: NonEmptyList[String], shardRange: Option[Range]): Stream[F, (String, Platform)] = {
    (sql"""
        SELECT token, platform
        FROM registrations
    """
      ++
      Fragments.whereAndOpt(
        Some(Fragments.in(fr"topic", topics)),
        shardRange.map(s => Fragments.and(fr"shard >= ${s.min}", fr"shard <= ${s.max}"))
      )
      ++ fr"GROUP BY token, platform"
      )
      .query[(String, String)]
      .stream
      .transact(xa)
      .map{ case(token, platformString) => {
        val maybePlatform = Platform.fromString(platformString)
        if(maybePlatform.isEmpty) {
          logger.error(s"Unknown platform in db $platformString")
        }
        (token, maybePlatform)
      }}
      .collect {
        case (token, Some(platform)) => (token, platform)
      }
  }
}
