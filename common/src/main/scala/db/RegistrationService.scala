package db

import cats.data.NonEmptyList
import cats.effect.internals.IOContextShift
import cats.effect.{Async, ContextShift, IO}
import doobie.util.transactor.Transactor
import fs2.Stream
import play.api.Configuration
import play.api.inject.ApplicationLifecycle

import scala.concurrent.ExecutionContext

class RegistrationService[F[_]: Async, S[_[_], _]](repository: RegistrationRepository[F, S]) {
  def findByToken(token: String): S[F, Registration] = repository.findByToken(token)
  def findByTopic(topic: Topic): S[F, Registration] = repository.findByTopic(topic)
  def save(sub: Registration): F[Int] = repository.save(sub)
  def remove(sub: Registration): F[Int] = repository.remove(sub)
  def removeAllByToken(token: String): F[Int] = repository.removeByToken(token)

  def countPerPlatformForTopics(topics: NonEmptyList[Topic]): F[PlatformCount] = topics match {
    case NonEmptyList(singleTopic, Nil) => repository.countPerPlatformForTopic(singleTopic)
    case moreThanOneTopic => repository.countPerPlatformForTopics(moreThanOneTopic)
  }
}


object RegistrationService {

  def apply[F[_]: Async](xa: Transactor[F]): RegistrationService[F, Stream] = {
    val repo = new SqlRegistrationRepository[F](xa)
    new RegistrationService[F, Stream](repo)
  }

  def fromConfig(config: Configuration, applicationLifecycle: ApplicationLifecycle)(implicit ec: ExecutionContext): RegistrationService[IO, Stream] = {

    implicit val contextShift: ContextShift[IO] = IOContextShift(ec)

    val url = config.get[String]("registration.db.url")
    val user = config.get[String]("registration.db.user")
    val password = config.get[String]("registration.db.password")
    val threads = config.get[Int]("registration.db.threads")

    val jdbcConfig = JdbcConfig("org.postgresql.Driver", s"jdbc:postgresql://$url", user, password, threads)
    val transactor = DatabaseConfig.transactor[IO](jdbcConfig, applicationLifecycle)

    apply(transactor)
  }
}
