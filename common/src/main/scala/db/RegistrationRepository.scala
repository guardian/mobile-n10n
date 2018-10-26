package db

import cats.data.NonEmptyList
import models.PlatformCount

trait RegistrationRepository[F[_], S[_[_], _]] {
  def findByTopic(topic: Topic): S[F, Registration]
  def findByToken(token: String): S[F, Registration]
  def save(sub: Registration): F[Int]
  def remove(sub: Registration): F[Int]
  def removeByToken(token: String): F[Int]
  def countPerPlatformForTopics(topics: NonEmptyList[Topic]): F[PlatformCount]
}
