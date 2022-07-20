package db

import java.util.concurrent.Executors

import cats.effect.{Async, Blocker, ContextShift, IO}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie.Transactor
import play.api.inject.ApplicationLifecycle

import scala.concurrent.{ExecutionContext, Future}

case class JdbcConfig(
  driverClassName: String,
  url: String,
  user: String,
  password: String,
  maxConnectionPoolSize: Int = 10
)

trait DatabaseConfig {
  def transactorAndDataSource[F[_] : Async](config: JdbcConfig)(implicit cs: ContextShift[F]): (Transactor[F], HikariDataSource)
}

object DatabaseConfig extends DatabaseConfig {

  def simpleTransactor(config: JdbcConfig)(implicit ec: ExecutionContext) = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)
    Transactor.fromDriverManager[IO](
      config.driverClassName,
      config.url,
      config.user,
      config.password
    )
  }
  def transactorAndDataSource[F[_] : Async](config: JdbcConfig)(implicit cs: ContextShift[F]): (Transactor[F], HikariDataSource) = {
    val connectEC = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))
    val transactEC = ExecutionContext.fromExecutor(Executors.newCachedThreadPool)
    val hikariConfig = new HikariConfig()
    hikariConfig.setDriverClassName("org.postgresql.Driver")
    hikariConfig.setMaximumPoolSize(config.maxConnectionPoolSize)
    hikariConfig.setJdbcUrl(config.url)
    hikariConfig.setUsername(config.user)
    hikariConfig.setPassword(config.password)
    hikariConfig.addDataSourceProperty("socketTimeout", "45")
    val dataSource = new HikariDataSource(hikariConfig)

    (Transactor.fromDataSource.apply(dataSource, connectEC, Blocker.liftExecutionContext(transactEC)), dataSource)
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


