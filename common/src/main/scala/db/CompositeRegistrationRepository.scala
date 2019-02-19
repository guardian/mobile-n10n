package db

import cats.data.NonEmptyList
import cats.effect.{Async, IO}
import cats.implicits._
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

  override def save(sub: Registration): IO[Int] = {
    val saves = NonEmptyList.of(master.save(sub), replica.save(sub))
    saves.parSequence.map(_.head)
  }

  override def remove(sub: Registration): IO[Int] =  {
    val deletes = NonEmptyList.of(master.remove(sub), replica.remove(sub))
    deletes.parSequence.map(_.head)
  }

  override def removeByToken(token: String): IO[Int] = {
    val deleteTokens = NonEmptyList.of(master.removeByToken(token), replica.removeByToken(token))
    deleteTokens.parSequence.map(_.head)
  }

  override def countPerPlatformForTopics(topics: NonEmptyList[Topic]): IO[PlatformCount] = master.countPerPlatformForTopics(topics)
}
