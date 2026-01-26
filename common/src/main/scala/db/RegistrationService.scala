package db

import cats.data.NonEmptyList
import cats.effect.internals.IOContextShift
import cats.effect.{Async, ContextShift, IO}
import doobie.free.connection.ConnectionIO
import doobie.util.transactor.Transactor
import fs2.Stream
import models.{Platform, ShardRange, TopicCount}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle

import cats.syntax.all._
import doobie.implicits._

import scala.concurrent.ExecutionContext

class RegistrationService[F[_]: Async, S[_[_], _]](repository: RegistrationRepository[F, S], xa: Transactor[F]) {
  def findByToken(token: String): S[F, Registration] = repository.findByToken(token)
  def findTokens(topics: NonEmptyList[Topic], shardRange: Option[ShardRange]): S[F, HarvestedToken] =
    repository.findTokens(topics.map(_.name), shardRange.map(_.range))

  def registerDevice(token: String, registrations: List[Registration]): F[Int] = {
    val prog = for {
      _ <- repository.deleteByToken(token)
      inserted <- registrations.map(repository.insert).sequence
    } yield inserted.sum
    prog.transact(xa)
  }

  def insert(registration: Registration): F[Int] = {
    repository.insert(registration).transact(xa)
  }

  def delete(registration: Registration): F[Int] = {
    repository.delete(registration).transact(xa)
  }

  def deleteByDate(olderThanDays: Int): F[Int] =
    repository.deleteByDate(olderThanDays).transact(xa)

  def removeAllByToken(token: String): F[Int] = {
    repository.deleteByToken(token).transact(xa)
  }

  def topicCounts(countThreshold: Int): S[F, TopicCount] = repository.topicCounts(countThreshold)

  def simpleSelectForHealthCheck(): S[F, TopicCount] = repository.simpleSelectForHealthCheck()
}


object RegistrationService {

  def apply[F[_]: Async](xa: Transactor[F]): RegistrationService[F, Stream] = {
    val repo = new SqlRegistrationRepository[F](xa)
    new RegistrationService[F, Stream](repo, xa)
  }

  def fromConfig(config: Configuration, applicationLifecycle: ApplicationLifecycle)(implicit ec: ExecutionContext): RegistrationService[IO, Stream] = {

    implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)

    val masterUrl = config.get[String]("registration.db.url")
    val user = config.get[String]("registration.db.user")
    val password = config.get[String]("registration.db.password")
    val threads = config.get[Int]("registration.db.maxConnectionPoolSize")

    val masterJdbcConfig = JdbcConfig("org.postgresql.Driver", masterUrl, user, password, threads)
    val masterTransactor = DatabaseConfig.transactor[IO](masterJdbcConfig, applicationLifecycle)

    apply(masterTransactor)
  }
}
