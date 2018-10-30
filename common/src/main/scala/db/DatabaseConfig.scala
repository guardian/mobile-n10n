package db

import java.util.concurrent.Executors

import cats.effect.internals.IOContextShift
import cats.effect.{Async, ContextShift, IO}
import com.zaxxer.hikari.HikariDataSource
import doobie.Transactor
import play.api.inject.ApplicationLifecycle

import scala.concurrent.{ExecutionContext, Future}

case class JdbcConfig(
  driverClassName: String,
  url: String,
  user: String,
  password: String,
  fixedThreadPool: Int = 20
)

object DatabaseConfig {

  def simpleTransactor(config: JdbcConfig)(implicit ec: ExecutionContext) = {
    implicit val cs: ContextShift[IO] = IOContextShift(ec)
    Transactor.fromDriverManager[IO](
      config.driverClassName,
      config.url,
      config.user,
      config.password
    )
  }
  private def transactorAndDataSource[F[_] : Async](config: JdbcConfig)(implicit cs: ContextShift[F]): (Transactor[F], HikariDataSource) = {
    val connectEC = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(config.fixedThreadPool))
    val transactEC = ExecutionContext.fromExecutor(Executors.newCachedThreadPool)
    val dataSource = new HikariDataSource()
    dataSource.setJdbcUrl(config.url)
    dataSource.setUsername(config.user)
    dataSource.setPassword(config.password)

    (Transactor.fromDataSource.apply(dataSource, connectEC, transactEC), dataSource)
  }

  def transactor[F[_] : Async](config: JdbcConfig)(implicit cs: ContextShift[F]): Transactor[F] = {
    val (transactor, _) = transactorAndDataSource(config)
    transactor
  }

  def transactor[F[_] : Async](config: JdbcConfig, applicationLifecycle: ApplicationLifecycle)(implicit cs: ContextShift[F]): Transactor[F] = {
    // manually creating the transactor to avoid having it wrapped in a Resource. Resources don't play well with
    // Play's way of handling lifecycle
    val (transactor, dataSource) = transactorAndDataSource(config)
    applicationLifecycle.addStopHook(() => Future.successful(dataSource.close()))
    transactor
  }
}


