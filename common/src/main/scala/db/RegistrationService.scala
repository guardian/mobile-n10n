package db

import cats.effect.Async
import doobie.hikari.HikariTransactor
import fs2.Stream

class RegistrationService[F[_], S[_[_], _]](repository: RegistrationRepository[F, S]) {
  def findByToken(token: String): S[F, Registration] = repository.findByToken(token)
  def findByTopic(topic: Topic): S[F, Registration] = repository.findByTopic(topic)
  def save(sub: Registration): F[Int] = repository.save(sub)
  def remove(sub: Registration): F[Int] = repository.remove(sub)
  def removeAllByToken(token: String): F[Int] = repository.removeByToken(token)
}


object RegistrationService {

    def apply[F[_]: Async](xa: HikariTransactor[F]): RegistrationService[F, Stream] = {
      val repo = new SqlRegistrationRepository[F](xa)
      new RegistrationService[F, Stream](repo)
    }
}
