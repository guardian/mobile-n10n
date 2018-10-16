package db

trait SubscriptionRepository[F[_], S[_[_], _]] {
  def findByTopic(topic: Topic): S[F, Subscription]
  def save(sub: Subscription): F[Int]
  def remove(sub: Subscription): F[Int]
}
