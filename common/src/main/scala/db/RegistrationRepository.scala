package db

import cats.data.NonEmptyList
import models.{PlatformCount, TopicCount}

trait RegistrationRepository[F[_], S[_[_], _]] {
  def findTokens(topics: NonEmptyList[String], platform: Option[String], shardRange: Option[Range]): S[F, String]
  def findByToken(token: String): S[F, Registration]
  def save(sub: Registration): F[Int]
  def remove(sub: Registration): F[Int]
  def removeByToken(token: String): F[Int]
  def topicCounts(countsThreshold: Int): S[F, TopicCount]
}
