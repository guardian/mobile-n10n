package db

import org.specs2.mutable.Specification
import doobie.implicits._
import cats.effect.IO
import cats.implicits._
import models.{Android, iOS}
import org.specs2.specification.BeforeAll
import fs2.Stream

class RegistrationServiceTest extends Specification with BeforeAll {

  val jdbcConfig = JdbcConfig("org.h2.Driver", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "", "")
  val transactor = DatabaseConfig.transactor[IO](jdbcConfig).unsafeRunSync()
  val service: RegistrationService[IO, fs2.Stream] = RegistrationService(transactor)

  def initializeDatabase() = {
    val drop =
      sql"""DROP TABLE IF EXISTS registrations"""
        .update.run

    val create =
      sql"""
        CREATE TABLE IF NOT EXISTS registrations(
          token VARCHAR NOT NULL,
          topic VARCHAR NOT NULL,
          platform VARCHAR NOT NULL,
          shard SMALLINT NOT NULL,
          lastModified TIMESTAMP WITH TIME ZONE NOT NULL,
          PRIMARY KEY (token, topic)
        )
        """
        .update.run

    (drop, create).mapN(_ + _).transact(transactor).unsafeRunSync
  }

  override def beforeAll() = initializeDatabase()

  def run[A](s: Stream[IO, A]): List[A] = s.compile.toList.unsafeRunSync()
  def run[A](io: IO[A]) = io.unsafeRunSync()

  lazy val reg1 = Registration(Device("a", Android), Topic("topic1"), Shard(1))
  lazy val reg2 = Registration(Device("b", iOS), Topic("topic1"), Shard(1))
  lazy val reg3 = Registration(Device("c", iOS), Topic("topic1"), Shard(1))
  lazy val reg4 = Registration(Device("d", Android), Topic("topic2"), Shard(1))
  lazy val reg5 = Registration(Device("e", iOS), Topic("topic3"), Shard(1))

  "RegistrationService" should {
    "allow adding registrations" in {
      run(service.save(reg1)) should equalTo(1)
      run(service.save(reg2)) should equalTo(1)
      run(service.save(reg3)) should equalTo(1)
      run(service.save(reg4)) should equalTo(1)
    }
    "allow finding registration by topic" in {
      run(service.findByTopic(Topic("topic1"))).length should equalTo(3)
      run(service.findByTopic(Topic("topic2"))).length should equalTo(1)
    }
    "allow removing registration" in {
      run(service.remove(reg1)) should equalTo(1)
      run(service.remove(reg4)) should equalTo(1)

      run(service.findByTopic(Topic("topic1"))).length should equalTo(2)
      run(service.findByTopic(Topic("topic2"))).length should equalTo(0)
    }
    "update when saving the same registration twice" in {
      run(service.save(reg5))
      val newShard = Shard(2)
      run(service.save(reg5.copy(shard = newShard)))

      run(service.findByTopic(reg5.topic)).head.shard should equalTo(newShard)
    }
  }

}
