package db

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all._
import doobie.implicits._
import doobie.util.transactor.Transactor
import fs2.Stream
import models.{ShardRange, TopicCount}

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
}
