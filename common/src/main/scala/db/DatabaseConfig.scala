package db

import cats.effect.Async
import doobie.hikari.HikariTransactor

case class JdbcConfig(driverClassName: String, url: String, user: String, password: String)

object DatabaseConfig {

  def transactor[F[_] : Async](config: JdbcConfig): F[HikariTransactor[F]] =
    HikariTransactor.newHikariTransactor[F](
      config.driverClassName,
      config.url,
      config.user,
      config.password
    )
}


