package tracking

import akka.actor.ActorSystem
import models.{Topic, TopicTypes}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.{AfterAll, Scope}

import scala.concurrent.duration._
import scala.concurrent.Future
import cats.implicits._
import org.mockito.Matchers.{eq => argEq}
class BatchingTopicSubscriptionsRepositorySpec(implicit ev: ExecutionEnv) extends Specification with Mockito with AfterAll {

  implicit val actorSystem = ActorSystem("BatchingTopicSubscriptionsRepositorySpec")

  "A BatchingTopicSubscriptionRepository" should {
    "Batch up multiple subscribe events" in new TestScope {
      batching.deviceSubscribed(topic1)
      batching.deviceSubscribed(topic1)
      batching.deviceSubscribed(topic1)
      batching.flush() must beEqualTo(()).awaitFor(10.seconds)
      there was one(underlying).deviceSubscribed(topic1, 3)
    }

    "Batch up multiple unsubscribe events" in new TestScope {
      batching.deviceUnsubscribed(topic1.id)
      batching.deviceUnsubscribed(topic1.id)
      batching.deviceUnsubscribed(topic1.id)
      batching.flush() must beEqualTo(()).awaitFor(10.seconds)
      there was one(underlying).deviceUnsubscribed(topic1.id, 3)
    }

    "Call underlying subscribe if more subs than unsubs" in new TestScope {
      batching.deviceSubscribed(topic1)
      batching.deviceSubscribed(topic1)
      batching.deviceSubscribed(topic1)
      batching.deviceUnsubscribed(topic1.id)
      batching.flush() must beEqualTo(()).awaitFor(10.seconds)
      there was one(underlying).deviceSubscribed(topic1, 2)
      there was no(underlying).deviceUnsubscribed(any[String], any[Int])
    }

    "Do nothing if subs equals unsubs" in new TestScope {
      batching.deviceSubscribed(topic1)
      batching.deviceSubscribed(topic1)
      batching.deviceSubscribed(topic1)
      batching.deviceUnsubscribed(topic1.id)
      batching.deviceUnsubscribed(topic1.id)
      batching.deviceUnsubscribed(topic1.id)
      batching.flush() must beEqualTo(()).awaitFor(10.seconds)
      there was no(underlying).deviceSubscribed(any[Topic], any[Int])
      there was no(underlying).deviceUnsubscribed(any[String], any[Int])
    }

    "Track different topics counts separately" in new TestScope {
      batching.deviceSubscribed(topic1)
      batching.deviceUnsubscribed(topic1.id)

      batching.deviceUnsubscribed(topic2.id)
      batching.deviceSubscribed(topic2)
      batching.deviceSubscribed(topic2)

      batching.deviceSubscribed(topic3)
      batching.deviceUnsubscribed(topic3.id)
      batching.deviceUnsubscribed(topic3.id)
      batching.deviceUnsubscribed(topic3.id)

      batching.flush() must beEqualTo(()).await

      there was no(underlying).deviceSubscribed(argEq(topic1), any[Int])
      there was no(underlying).deviceUnsubscribed(argEq(topic1.id), any[Int])

      there was one(underlying).deviceSubscribed(topic2, 1)
      there was no(underlying).deviceUnsubscribed(argEq(topic2.id), any[Int])

      there was no(underlying).deviceSubscribed(argEq(topic3), any[Int])
      there was one(underlying).deviceUnsubscribed(topic3.id, 2)
    }
  }

  trait TestScope extends Scope {
    val underlying = mock[TopicSubscriptionsRepository]
    underlying.deviceSubscribed(any[Topic], any[Int]).returns(Future.successful(().right))
    underlying.deviceUnsubscribed(any[String], any[Int]).returns(Future.successful(().right))
    val batching = new BatchingTopicSubscriptionsRepository(underlying)
    val topic1 = Topic(TopicTypes.Breaking, "test1")
    val topic2 = Topic(TopicTypes.Breaking, "test2")
    val topic3 = Topic(TopicTypes.Breaking, "test3")
  }

  override def afterAll(): Unit = {
    actorSystem.terminate()
  }
}
