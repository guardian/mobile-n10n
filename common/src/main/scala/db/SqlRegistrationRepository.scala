package db

import cats.effect.Async
import cats.implicits._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import fs2.Stream
import cats.data.NonEmptyList
import db.BuildTier.BuildTier
import doobie.free.connection.ConnectionIO
import doobie.Fragments
import models.{Platform, TopicCount}
import org.slf4j.{Logger, LoggerFactory}

import Registration._

class SqlRegistrationRepository[F[_]: Async](xa: Transactor[F])
  extends RegistrationRepository[F, Stream] {
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def findByToken(token: String): Stream[F, Registration] = {
    sql"""
         SELECT token, platform, topic, shard, lastModified, buildTier
         FROM registrations
         WHERE token = $token
      """
      .query[Registration]
      .stream
      .transact(xa)
  }

  override def delete(reg: Registration): ConnectionIO[Int] = sql"""
        DELETE FROM registrations WHERE token = ${reg.device.token} AND topic = ${reg.topic.name}
      """
      .update.run


  override def deleteByToken(token: String): ConnectionIO[Int] = {
    sql"""
      DELETE FROM registrations WHERE token = $token
    """
    .update.run
  }

  override def deleteByDate(olderThanDays: Int): ConnectionIO[Int] = {
    sql"""
      delete from registrations.registrations where lastmodified <= now() - make_interval(days => $olderThanDays);
    """.update.run
  }

  def insert(reg: Registration): ConnectionIO[Int] = {
    val buildTierForDb: Option[String] = reg.buildTier.map(_.toString)
    sql"""
        INSERT INTO registrations (token, platform, topic, shard, lastModified, buildTier)
        VALUES (
          ${reg.device.token},
          ${reg.device.platform},
          ${reg.topic.name},
          ${reg.shard.id},
          CURRENT_TIMESTAMP,
          $buildTierForDb
        )
      """
    .update.run
  }

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

  /**
   * Used to verify that the DB connection is healthy.
   * We just select one topic and a constant value as this is sufficient to check connectivity (we don't care about what data is returned).
   */
  override def simpleSelectForHealthCheck(): Stream[F, TopicCount] = {
    sql"""
         SELECT  topic
                , 1
         FROM   registrations
         LIMIT  1
      """
      .query[TopicCount]
      .stream
      .transact(xa)
  }

  override def findTokens(topics: NonEmptyList[String], shardRange: Option[Range]): Stream[F, HarvestedToken] = {
    val queryStatement = (sql"""
        SELECT token, platform, buildTier
        FROM registrations
    """
      ++
      Fragments.whereAndOpt(
        Some(Fragments.in(fr"topic", topics)),
        shardRange.map(s => Fragments.and(fr"shard >= ${s.min}", fr"shard <= ${s.max}"))
      )
      ++ fr"GROUP BY token, platform, buildTier"
      )

    logger.info("About to run query: " + queryStatement);

    val result = queryStatement
      .query[(String, String, Option[String])]
      .stream
      .transact(xa)

    logger.info("Result: " + result.zipWithIndex)

    result.map{ case (token, platformString, buildTierString) => {
      val maybePlatform = Platform.fromString(platformString)
      if(maybePlatform.isEmpty) {
        logger.error(s"Unknown platform in db $platformString")
      }
      val maybeBuildTier: Option[BuildTier] = buildTierString.flatMap(BuildTier.fromString)
      (token, maybePlatform, maybeBuildTier)
    }}
    .collect {
      case (token, Some(platform), buildTier) => HarvestedToken(token, platform, buildTier)
    }
  }
}
