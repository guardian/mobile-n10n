package tracking
import akka.actor.ActorSystem
import models.Topic
import tracking.Repository.RepositoryResult

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.util.Timeout
import cats.implicits._

import scala.concurrent.duration.FiniteDuration

object BatchingTopicSubscriptionsRepository {

  case class SubscriptionEvent(topic: Option[Topic], topicId: String, count: Int)

  case object Flush

  private class InternalActor(underlying: TopicSubscriptionsRepository) extends Actor {

    implicit val ec = context.dispatcher

    var events = collection.mutable.Buffer[SubscriptionEvent]()

    override def receive: Actor.Receive = {
      case event: SubscriptionEvent =>
        events.append(event)

      case Flush =>
        if (events.nonEmpty) {
          val requestor = sender
          flush() foreach { _ => requestor ! () }
        } else {
          sender ! ()
        }
    }

    def flush(): Future[Unit] = {
      val eventsByTopic = events.toList.groupBy(_.topicId)
      val result = Future.sequence(eventsByTopic.map(submitSubscriptions)).map(_ => ())
      events = collection.mutable.Buffer[SubscriptionEvent]()
      result
    }

    private def submitSubscriptions: PartialFunction[(String, List[SubscriptionEvent]), Future[Unit]] = {
      case (topicId, topicEvents) =>
        val subscriptionCount = topicEvents.map(_.count).sum

        val result = if (subscriptionCount < 0) {
          Some(underlying.deviceUnsubscribed(topicId, -subscriptionCount))
        } else if (subscriptionCount > 0) {
          val topic = topicEvents.collectFirst { case SubscriptionEvent(Some(t), _, _) => t }
          topic.map { topic => underlying.deviceSubscribed(topic, subscriptionCount) }
        } else None

        def futureToUnit[T](f: Future[T]) = f.map(_ => ())

        def defaultSuccess = Future.successful(())

        result.map(futureToUnit).getOrElse(defaultSuccess)
    }
  }
}
class BatchingTopicSubscriptionsRepository(underlying: TopicSubscriptionsRepository)(implicit val system: ActorSystem) extends TopicSubscriptionsRepository {

  import BatchingTopicSubscriptionsRepository._

  implicit val ec = system.dispatcher

  val actor = system.actorOf(Props(classOf[InternalActor], underlying))

  override def deviceSubscribed(topic: Topic, count: Int = 1): Future[RepositoryResult[Unit]] = {
    actor ! SubscriptionEvent(Some(topic), topic.id, count)
    Future.successful(().right)
  }

  override def deviceUnsubscribed(topicId: String, count: Int = 1): Future[RepositoryResult[Unit]] = {
    actor ! SubscriptionEvent(None, topicId, -count)
    Future.successful(().right)
  }

  override def count(topic: Topic): Future[RepositoryResult[Int]] = underlying.count(topic)

  def flush(): Future[Unit] = {
    implicit val timeout = Timeout(1.minute)
    (actor ? Flush).mapTo[Unit]
  }

  def scheduleFlush(interval: FiniteDuration): Unit = {
    system.scheduler.schedule(interval, interval, actor, Flush)
  }
}
