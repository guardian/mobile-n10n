package db

import org.specs2.mutable.Specification
import doobie.implicits._
import cats.effect.IO
import cats.implicits._
import models.{Android, iOS}
import org.specs2.specification.BeforeAll

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
          token TEXT,
          topic TEXT,
          platform TEXT,
          shard SMALLINT,
          lastModified TIMESTAMP WITH TIME ZONE
        )
        """
        .update.run

    (drop, create).mapN(_ + _).transact(transactor).unsafeRunSync
  }

  override def beforeAll() = initializeDatabase()

  lazy val date = new java.sql.Date(0)
  lazy val reg1 = Registration(Device("a", Android), Topic("topic1"), Shard(1), date)
  lazy val reg2 = Registration(Device("b", iOS), Topic("topic1"), Shard(1), date)
  lazy val reg3 = Registration(Device("c", iOS), Topic("topic1"), Shard(1), date)
  lazy val reg4 = Registration(Device("d", Android), Topic("topic2"), Shard(1), date)


  "RegistrationService" should {
    "allow adding registrations" in {
      service.save(reg1).unsafeRunSync() should equalTo(1)
      service.save(reg2).unsafeRunSync() should equalTo(1)
      service.save(reg3).unsafeRunSync() should equalTo(1)
      service.save(reg4).unsafeRunSync() should equalTo(1)
    }
    "allow finding registration by topic" in {
      service.findByTopic(Topic("topic1")).compile.toList.unsafeRunSync().length should equalTo(3)
      service.findByTopic(Topic("topic2")).compile.toList.unsafeRunSync().length should equalTo(1)
    }
    "allow removing registration" in {
      service.remove(reg1).unsafeRunSync() should equalTo(1)
      service.remove(reg4).unsafeRunSync() should equalTo(1)


      service.findByTopic(Topic("topic1")).compile.toList.unsafeRunSync().length should equalTo(2)
      service.findByTopic(Topic("topic2")).compile.toList.unsafeRunSync().length should equalTo(0)
    }
  }

}
