package db

import cats.data.NonEmptyList
import org.specs2.mutable.Specification
import doobie.implicits._
import cats.effect.IO
import cats.implicits._
import doobie.util.transactor.Transactor
import models.{Android, PlatformCount, iOS}
import org.specs2.specification.BeforeAll
import fs2.Stream
import org.specs2.concurrent.ExecutionEnv

class RegistrationServiceTest(implicit ee: ExecutionEnv) extends Specification with BeforeAll {

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
          PRIMARY KEY (token, topic)
        )
        """
        .update.run

    (drop, create).mapN(_ + _).transact(transactor).unsafeRunSync
  }

  override def beforeAll() = initializeDatabase()

  def run[A](s: Stream[IO, A]): List[A] = s.compile.toList.unsafeRunSync()
  def run[A](io: IO[A]) = io.unsafeRunSync()

  val registrations = Seq(
    Registration(Device("a", Android), Topic("topic1"), Shard(1)),
    Registration(Device("b", iOS), Topic("topic1"), Shard(1)),
    Registration(Device("c", iOS), Topic("topic1"), Shard(1)),
    Registration(Device("d", iOS), Topic("topic1"), Shard(2)),
    Registration(Device("e", Android), Topic("topic2"), Shard(1)),
    Registration(Device("f", iOS), Topic("topic3"), Shard(1)),
    Registration(Device("f", Android), Topic("topic4"), Shard(1))
  )

  def shardOrdering: Ordering[Registration] = _.shard.id compare _.shard.id
  val allShards = ShardRange(registrations.min(shardOrdering).shard, registrations.max(shardOrdering).shard)
  val shardRange1 = ShardRange(Shard(1), Shard(1))
  val shardRange2 = ShardRange(Shard(2), Shard(2))

  "RegistrationService" should {
    "allow adding registrations" in {
      registrations.map { reg =>
        run(service.save(reg)) should equalTo(1)
      }
    }
    "allow finding registrations by topic, platform and shard" in {
      run(service.find(Topic("topic1"), iOS, allShards)).length should equalTo(3)
      run(service.find(Topic("topic1"), iOS, shardRange1)).length should equalTo(2)
      run(service.find(Topic("topic2"), Android, shardRange1)).length should equalTo(1)
      run(service.find(Topic("topic2"), Android, shardRange2)).length should equalTo(0)
    }
    "allow finding registrations by token" in {
      run(service.findByToken("f")).length should equalTo(2)
      run(service.findByToken("unknown")).length should equalTo(0)
    }
    "allow removing registrations" in {

      val regB = registrations.filter(_.device.token == "b").head
      val regE = registrations.filter(_.device.token == "e").head
      run(service.remove(regB)) should equalTo(1)
      run(service.remove(regE)) should equalTo(1)
      run(service.find(Topic("topic1"), iOS, allShards)).length should equalTo(2)
      run(service.find(Topic("topic2"), Android, allShards)).length should equalTo(0)
    }
    "allow removing registrations by token" in {
      run(service.removeAllByToken("f")) should equalTo(2)
      run(service.findByToken("f")).length should equalTo(0)
    }
    "update when saving the same registration twice" in {
      val reg = registrations.filter(_.device.token == "a").head
      val oldShard = reg.shard
      run(service.save(reg))
      val newShard = Shard(99)
      run(service.save(reg.copy(shard = newShard)))

      def singleShardRange(shard: Shard) = ShardRange(shard, shard)

      run(service.find(reg.topic, reg.device.platform, singleShardRange(oldShard))).length should equalTo(0)
      run(service.find(reg.topic, reg.device.platform, singleShardRange(newShard))).head.shard should equalTo(newShard)
    }
    "return 0 if no registration has that topic" in {
      run(service.countPerPlatformForTopics(NonEmptyList.one(Topic("idontexist")))) shouldEqual PlatformCount(0,0,0,0)
      run(service.countPerPlatformForTopics(NonEmptyList(Topic("idontexist"), List(Topic("neitherdoi"))))) shouldEqual PlatformCount(0,0,0,0)
    }
    "count per platform and per topic with one topic" in {
      run(service.countPerPlatformForTopics(NonEmptyList.one(Topic("topic3")))) shouldEqual PlatformCount(1,1,0,0)
    }
    "count per platform and per topic with two or more topics" in {
      run(service.countPerPlatformForTopics(NonEmptyList(Topic("topic3"), List(Topic("topic4"))))) shouldEqual PlatformCount(1,1,0,0)
    }
  }

}
