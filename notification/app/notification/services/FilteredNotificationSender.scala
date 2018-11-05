package notification.services
import models._
import notification.models.Push
import notification.services.guardian.{GuardianNotificationSender, TopicRegistrationCounter}
import org.joda.time.DateTime
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class FilteredNotificationSender(
  notificationSender: NotificationSender,
  topicRegistrationCounter: TopicRegistrationCounter,
  invertCondition: Boolean
)(implicit ec: ExecutionContext) extends NotificationSender {

  private val logger: Logger = Logger.apply(classOf[FilteredNotificationSender])

  val allowedTopicTypes: Set[TopicType] = Set(TopicTypes.Content, TopicTypes.TagContributor, TopicTypes.TagSeries)
  val maxRegistrationCount: Int = 100

  override def sendNotification(push: Push): Future[SenderResult] = {
    def shouldSend(count: PlatformCount, topics: List[Topic]): Boolean = {
      count.total < maxRegistrationCount && topics.forall(t => allowedTopicTypes.contains(t.`type`))
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

    def defaultCountRecovery: PartialFunction[Throwable, PlatformCount] = {
      case NonFatal(e) if push.notification.topic.exists(_.`type` == TopicTypes.Breaking) =>
        logger.error(s"Unable to count registration for topics ${push.notification.topic}, falling back to default value", e)
        PlatformCount(1500000, 750000, 750000, 0)
      case NonFatal(e) =>
        logger.error(s"Unable to count registration for topics ${push.notification.topic}, falling back to default value", e)
        PlatformCount(maxRegistrationCount, 1, 1, 0)
    }

    for {
      count <- topicRegistrationCounter.count(push.notification.topic).recover(defaultCountRecovery)
      result <- sendOrFilter(count, push.notification.topic)
    } yield result
  }
}
