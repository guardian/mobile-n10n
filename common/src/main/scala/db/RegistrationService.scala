package db

import cats.data.NonEmptyList
import cats.effect.internals.IOContextShift
import cats.effect.{Async, ContextShift, IO}
import doobie.util.transactor.Transactor
import fs2.Stream
import models.{Platform, PlatformCount, ShardRange, TopicCount}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle

import scala.concurrent.ExecutionContext

class RegistrationService[F[_], S[_[_], _]](repository: RegistrationRepository[F, S]) {
  def findByToken(token: String): S[F, Registration] = repository.findByToken(token)
  def findTokens(topics: NonEmptyList[Topic], platform: Option[Platform], shardRange: Option[ShardRange]): S[F, String] =
    repository.findTokens(topics.map(_.name), platform.map(_.toString), shardRange.map(_.range))
  def save(sub: Registration): F[Int] = repository.save(sub)
  def remove(sub: Registration): F[Int] = repository.remove(sub)
  def removeAllByToken(token: String): F[Int] = repository.removeByToken(token)
  def countPerPlatformForTopics(topics: NonEmptyList[Topic]): F[PlatformCount] = repository.countPerPlatformForTopics(topics)
  def topicCounts(countThreshold: Int): S[F, TopicCount] = repository.topicCounts(countThreshold)
}


object RegistrationService {

  def apply[F[_]: Async](xa: Transactor[F]): RegistrationService[F, Stream] = {
    val repo = new SqlRegistrationRepository[F](xa)
    new RegistrationService[F, Stream](repo)
  }

  def fromConfig(config: Configuration, applicationLifecycle: ApplicationLifecycle)(implicit ec: ExecutionContext): RegistrationService[IO, Stream] = {

    implicit val contextShift: ContextShift[IO] = IOContextShift(ec)

    val masterUrl = config.get[String]("registration.db.url")
    val user = config.get[String]("registration.db.user")
    val password = config.get[String]("registration.db.password")
    val threads = config.get[Int]("registration.db.maxConnectionPoolSize")

    val masterJdbcConfig = JdbcConfig("org.postgresql.Driver", masterUrl, user, password, threads)
    val masterTransactor = DatabaseConfig.transactor[IO](masterJdbcConfig, applicationLifecycle)

    apply(masterTransactor)
  }
}
