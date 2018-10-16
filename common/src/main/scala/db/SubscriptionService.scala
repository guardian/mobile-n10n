package db

import cats.effect.Async
import doobie.hikari.HikariTransactor
import fs2.Stream

class SubscriptionService[F[_], S[_[_], _]](repository: SubscriptionRepository[F, S]) {
  def findByTopic(topic: Topic): S[F, Subscription] = repository.findByTopic(topic)
  def save(sub: Subscription): F[Int] = repository.save(sub)
  def remove(sub: Subscription): F[Int] = repository.remove(sub)
}


object SubscriptionService {

    def apply[F[_]: Async](xa: HikariTransactor[F]): SubscriptionService[F, Stream] = {
      val repo = new SqlSubscriptionRepository[F](xa)
      new SubscriptionService[F, Stream](repo)
    }
}
