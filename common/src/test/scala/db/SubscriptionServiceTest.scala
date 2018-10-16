package db

import org.specs2.mutable.Specification
import doobie.implicits._
import cats.effect.IO
import cats.implicits._
import org.specs2.specification.BeforeAll

class SubscriptionServiceTest extends Specification with BeforeAll {

  val jdbcConfig = JdbcConfig("org.h2.Driver", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "", "")
  val transactor = DatabaseConfig.transactor[IO](jdbcConfig).unsafeRunSync()
  val service: SubscriptionService[IO, fs2.Stream] = SubscriptionService(transactor)

  def initializeDatabase() = {
    val drop =
      sql"""DROP TABLE IF EXISTS subscriptions"""
        .update.run

    val create =
      sql"""
        CREATE TABLE subscriptions (
          token VARCHAR,
          platform VARCHAR,
          topic VARCHAR,
          shard VARCHAR,
          last_modified DATE
        )"""
        .update.run

    (drop, create).mapN(_ + _).transact(transactor).unsafeRunSync
  }

  override def beforeAll() = initializeDatabase()

  lazy val date = new java.sql.Date(0)
  lazy val sub1 = Subscription(Device("a", Platform.Android), Topic("topic1"), Shard("s"), date)
  lazy val sub2 = Subscription(Device("b", Platform.Apple), Topic("topic1"), Shard("s"), date)
  lazy val sub3 = Subscription(Device("c", Platform.Apple), Topic("topic1"), Shard("s"), date)
  lazy val sub4 = Subscription(Device("d", Platform.Android), Topic("topic2"), Shard("s"), date)


  "SubscriptionService" should {
    "allow adding subscriptions" in {
      service.save(sub1).unsafeRunSync() should equalTo(1)
      service.save(sub2).unsafeRunSync() should equalTo(1)
      service.save(sub3).unsafeRunSync() should equalTo(1)
      service.save(sub4).unsafeRunSync() should equalTo(1)
    }
    "allow finding subscriptions by topic" in {
      service.findByTopic(Topic("topic1")).compile.toList.unsafeRunSync().length should equalTo(3)
      service.findByTopic(Topic("topic2")).compile.toList.unsafeRunSync().length should equalTo(1)
    }
    "allow removing subscriptions" in {
      service.remove(sub1).unsafeRunSync() should equalTo(1)
      service.remove(sub4).unsafeRunSync() should equalTo(1)


      service.findByTopic(Topic("topic1")).compile.toList.unsafeRunSync().length should equalTo(2)
      service.findByTopic(Topic("topic2")).compile.toList.unsafeRunSync().length should equalTo(0)
    }
  }

}
