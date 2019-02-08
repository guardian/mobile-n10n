package db

import cats.data.NonEmptyList
import cats.effect.{Async, IO}
import fs2.Stream
import models.PlatformCount

class CompositeRegistrationRepository(
  master: RegistrationRepository[IO, Stream],
  replica: RegistrationRepository[IO, Stream]
) extends RegistrationRepository[IO, Stream] {
  override def findTokens(topics: NonEmptyList[String], platform: Option[String], shardRange: Option[Range]): Stream[IO, String] = {
    master.findTokens(topics, platform, shardRange)
  }

  override def findByToken(token: String): Stream[IO, Registration] = {
    master.findByToken(token)
  }

  override def save(sub: Registration): IO[Int] = for {
    masterCount <- master.save(sub)
    replicaCount <- replica.save(sub)
  }  yield masterCount

  override def remove(sub: Registration): IO[Int] = for {
    masterCount <- master.remove(sub)
    replicaCounnt <- replica.remove(sub)
  } yield masterCount

  override def removeByToken(token: String): IO[Int] = for {
    masterCount <- master.removeByToken(token)
    replicaCounnt <- replica.removeByToken(token)
  } yield masterCount

  override def countPerPlatformForTopics(topics: NonEmptyList[Topic]): IO[PlatformCount] = master.countPerPlatformForTopics(topics)
}
