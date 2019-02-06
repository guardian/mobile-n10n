package db

import cats.data.NonEmptyList
import org.specs2.mutable.Specification
import doobie.implicits._
import cats.effect.IO
import cats.implicits._
import doobie.util.transactor.Transactor
import models.{Android, PlatformCount, ShardRange, iOS}
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
          lastModified TIMESTAMP WITH TIME ZONE,
          lastmodifiedepochmillis BIGINT,
          PRIMARY KEY (token, topic)
        )
        """
        .update.run

    (drop, create).mapN(_ + _).transact(transactor).unsafeRunSync
  }

  override def before() = {
    initializeDatabase()
    registrations.map(service.save).foreach(io => run(io))
  }

  def run[A](s: Stream[IO, A]): List[A] = s.compile.toList.unsafeRunSync()
  def run[A](io: IO[A]) = io.unsafeRunSync()

  val registrations = Seq(
    Registration(Device("a", Android), Topic("topic1"), Shard(1), None, None),
    Registration(Device("b", iOS), Topic("topic1"), Shard(1), None, None),
    Registration(Device("c", iOS), Topic("topic1"), Shard(1), None, None),
    Registration(Device("d", iOS), Topic("topic1"), Shard(2), None, None),
    Registration(Device("e", Android), Topic("topic2"), Shard(1), None, None),
    Registration(Device("f", iOS), Topic("topic3"), Shard(1), None, None),
    Registration(Device("f", Android), Topic("topic4"), Shard(1), None, None)
  )

  def shardOrdering: Ordering[Registration] = _.shard.id compare _.shard.id
  val allShards = ShardRange(registrations.min(shardOrdering).shard.id, registrations.max(shardOrdering).shard.id)
  val shardRange1 = ShardRange(1, 1)
  val shardRange2 = ShardRange(2, 2)

  val topicsAll: NonEmptyList[Topic] =
    NonEmptyList.of(registrations.head.topic, registrations.tail.map(_.topic):_*)
  val topics1 = NonEmptyList.one(Topic("topic1"))
  val topics2 = NonEmptyList.one(Topic("topic2"))

  "RegistrationService" should {
    "allow adding registration" in {
      val reg = Registration(Device("something", Android), Topic("someTopic"), Shard(1), None, None)
      run(service.save(reg)) should equalTo(1)
    }
    "allow finding registrations by topics, platform and shard" in {
      run(service.findTokens(topicsAll, None, None)).length should equalTo(6)
      run(service.findTokens(topics1, None, None)).length should equalTo(4)
      run(service.findTokens(topics1, Some(iOS), None)).length should equalTo(3)
      run(service.findTokens(topics1, Some(iOS), Some(allShards))).length should equalTo(3)
      run(service.findTokens(topics1, Some(iOS), Some(shardRange1))).length should equalTo(2)
      run(service.findTokens(topics2, Some(Android), Some(shardRange1))).length should equalTo(1)
      run(service.findTokens(topics2, Some(Android), Some(shardRange2))).length should equalTo(0)
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
      run(service.findByToken(regB.device.token)).length should equalTo(0)
      run(service.findByToken(regE.device.token)).length should equalTo(0)
    }
    "allow removing registrations by token" in {
      run(service.removeAllByToken("f")) should equalTo(2)
      run(service.findByToken("f")).length should equalTo(0)
    }
    "update when saving the same registration twice" in {
      val reg = registrations.filter(_.device.token == "a").head
      val newShardId: Short = 99
      run(service.save(reg.copy(shard = Shard(newShardId))))
      run(service.findByToken(reg.device.token)).head.shard.id should equalTo(newShardId)
    }
    "return 0 if no registration has that topic" in {
      run(service.countPerPlatformForTopics(NonEmptyList.one(Topic("idontexist")))) shouldEqual PlatformCount(0,0,0,0)
      run(service.countPerPlatformForTopics(NonEmptyList(Topic("idontexist"), List(Topic("neitherdoi"))))) shouldEqual PlatformCount(0,0,0,0)
    }
    "count per platform and per topic with one topic" in {
      run(service.countPerPlatformForTopics(NonEmptyList.one(Topic("topic3")))) shouldEqual PlatformCount(1,1,0,0)
    }
    "count per platform and per topic with two or more topics" in {
      run(service.countPerPlatformForTopics(NonEmptyList(Topic("topic3"), List(Topic("topic4"))))) shouldEqual PlatformCount(2,1,1,0)
    }
  }

}
