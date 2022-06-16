package db

import cats.data.NonEmptyList
import org.specs2.mutable.Specification
import doobie.implicits._
import cats.effect.IO
import cats.syntax.all._
import doobie.util.transactor.Transactor
import models.{Android, ShardRange, Ios}
import org.specs2.specification.BeforeEach
import fs2.Stream
import org.specs2.concurrent.ExecutionEnv

class RegistrationServiceSpec(implicit ee: ExecutionEnv) extends Specification with BeforeEach {

  val jdbcConfig = JdbcConfig("org.h2.Driver", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "", "")
  val transactor: Transactor[IO] = DatabaseConfig.simpleTransactor(jdbcConfig)
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
          buildTier VARCHAR,
          PRIMARY KEY (token, topic)
        )
        """
        .update.run

    (drop, create).mapN(_ + _).transact(transactor).unsafeRunSync
  }

  override def before() = {
    initializeDatabase()
    registrations.map(service.insert).foreach(io => run(io))
  }

  def run[A](s: Stream[IO, A]): List[A] = s.compile.toList.unsafeRunSync()
  def run[A](io: IO[A]) = io.unsafeRunSync()

  val registrations = Seq(
    Registration(Device("a", Android), Topic("topic1"), Shard(1), None, None),
    Registration(Device("b", Ios), Topic("topic1"), Shard(1), None, None),
    Registration(Device("c", Ios), Topic("topic1"), Shard(1), None, None),
    Registration(Device("d", Ios), Topic("topic1"), Shard(2), None, None),
    Registration(Device("e", Android), Topic("topic2"), Shard(1), None, None),
    Registration(Device("f", Ios), Topic("topic3"), Shard(1), None, None),
    Registration(Device("f", Android), Topic("topic4"), Shard(1), None, Some(BuildTier.RELEASE))
  )

  def shardOrdering: Ordering[Registration] = _.shard.id compare _.shard.id
  val allShards = ShardRange(registrations.min(shardOrdering).shard.id, registrations.max(shardOrdering).shard.id)
  val shardRange1 = ShardRange(1, 1)
  val shardRange2 = ShardRange(2, 2)

  val topicsAll: NonEmptyList[Topic] =
    NonEmptyList.of(Topic("topic1"), Topic("topic2"), Topic("topic3"), Topic("topic4"))
  val topics1 = NonEmptyList.one(Topic("topic1"))
  val topics2 = NonEmptyList.one(Topic("topic2"))

  "RegistrationService" should {

    "allow adding registration (without a build tier specified)" in {
      val reg = Registration(Device("something", Android), Topic("someTopic"), Shard(1), None, None)
      run(service.insert(reg)) should equalTo(1)
    }

    "allow adding registration (with a build tier specified)" in {
      val reg = Registration(Device("something", Android), Topic("someTopic"), Shard(1), None, Some(BuildTier.RELEASE))
      run(service.insert(reg)) should equalTo(1)
    }

    "allow finding registrations by topics and shard" in {
      run(service.findTokens(topicsAll, None)).length should equalTo(7)
      run(service.findTokens(topics1, None)).length should equalTo(4)
      run(service.findTokens(topics1, Some(allShards))).length should equalTo(4)
      run(service.findTokens(topics1, Some(shardRange1))).length should equalTo(3)
      run(service.findTokens(topics2, Some(shardRange1))).length should equalTo(1)
      run(service.findTokens(topics2, Some(shardRange2))).length should equalTo(0)
    }
    "allow finding registrations by token" in {
      run(service.findByToken("f")).length should equalTo(2)
      run(service.findByToken("unknown")).length should equalTo(0)
    }
    "allow removing registrations" in {
      val regB = registrations.filter(_.device.token == "b").head
      val regE = registrations.filter(_.device.token == "e").head
      run(service.delete(regB)) should equalTo(1)
      run(service.delete(regE)) should equalTo(1)
      run(service.findByToken(regB.device.token)).length should equalTo(0)
      run(service.findByToken(regE.device.token)).length should equalTo(0)
    }
    "allow removing registrations by token" in {
      run(service.removeAllByToken("f")) should equalTo(2)
      run(service.findByToken("f")).length should equalTo(0)
    }
  }
}
