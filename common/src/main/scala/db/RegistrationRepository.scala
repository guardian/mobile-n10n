package db

import cats.data.NonEmptyList
import doobie.free.connection.ConnectionIO
import models.{Platform, TopicCount}

trait RegistrationRepository[F[_], S[_[_], _]] {
  def findTokens(topics: NonEmptyList[String], shardRange: Option[Range]): S[F, (String, Platform)]
  def findByToken(token: String): S[F, Registration]
  def insert(reg: Registration): ConnectionIO[Int]
  def delete(sub: Registration): ConnectionIO[Int]
  def deleteByToken(token: String): ConnectionIO[Int]
  def topicCounts(countsThreshold: Int): S[F, TopicCount]
}
