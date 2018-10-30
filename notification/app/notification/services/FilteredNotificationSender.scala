package notification.services
import models._
import notification.models.Push
import notification.services.guardian.TopicRegistrationCounter
import org.joda.time.DateTime

import scala.concurrent.{ExecutionContext, Future}

class FilteredNotificationSender(
  notificationSender: NotificationSender,
  topicRegistrationCounter: TopicRegistrationCounter,
  invertCondition: Boolean
)(implicit ec: ExecutionContext) extends NotificationSender {

  val ALLOWED_TOPICS: Set[TopicType] = Set(TopicTypes.Content, TopicTypes.TagContributor, TopicTypes.TagSeries)

  override def sendNotification(push: Push): Future[SenderResult] = {
    def shouldSend(count: PlatformCount, topics: List[Topic]): Boolean = {
      count.total <= 10 && topics.forall(t => ALLOWED_TOPICS.contains(t.`type`))
    }

    def sendOrFilter(count: PlatformCount, topics: List[Topic]): Future[SenderResult] = {
      // ^ is a XOR, which means we invert the condition if invertCondition is true
      if (shouldSend(count, topics) ^ invertCondition) {
        notificationSender.sendNotification(push)
      } else {
        Future.successful {
          Right(SenderReport(senderName = "Filtered", sentTime = DateTime.now))
        }
      }
    }

    for {
      count <- topicRegistrationCounter.count(push.notification.topic)
      result <- sendOrFilter(count, push.notification.topic)
    } yield result
  }
}
