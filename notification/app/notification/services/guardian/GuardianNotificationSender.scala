package notification.services.guardian

import com.amazonaws.services.sqs.AmazonSQSAsync
import notification.models.Push
import notification.services.{NotificationSender, SenderError, SenderResult}
import aws.AWSAsync._
import com.amazonaws.services.sqs.model.{SendMessageBatchRequest, SendMessageBatchRequestEntry, SendMessageBatchResult}
import models.Provider.Guardian
import models._
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.Json

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

case class GuardianFailedToQueueShard(
  senderName: String,
  reason: String
) extends SenderError

class GuardianNotificationSender(
  sqsClient: AmazonSQSAsync,
  registrationCounter: TopicRegistrationCounter,
  platform: Platform,
  sqsArn: String,
)(implicit ec: ExecutionContext) extends NotificationSender {

  val BATCH_SIZE: Int = 10000

  private val logger: Logger = Logger.apply(classOf[GuardianNotificationSender])

  override def sendNotification(push: Push): Future[SenderResult] = for {
      topicStats <- registrationCounter.count(push.notification.topic)
      registrationCount = topicStats.counts.getOrElse(platform, 10)
      batch = prepareBatch(platform, push.notification, registrationCount)
      batchResult <- sendBatch(batch)
  } yield {
    val failed = Option(batchResult.getFailed).map(_.asScala.toList).getOrElse(Nil)
    if (failed.isEmpty) {
      Right(SenderReport(
        Guardian.value,
        DateTime.now,
        None,
        Some(PlatformStatistics(
          platform,
          registrationCount
        ))
      ))
    } else {
      failed.foreach { failure =>
        logger.error(s"Unable to queue notification ${push.notification.id} for platform $platform: " +
          s"${failure.getId} - ${failure.getCode} - ${failure.getMessage}")
      }
      Left(GuardianFailedToQueueShard(
        senderName = s"Guardian $platform",
        reason = s"Unable to queue notification. Please check the logs for notification ${push.notification.id}")
      )
    }
  }

  def sendBatch(batch: List[SendMessageBatchRequestEntry]): Future[SendMessageBatchResult] = {
    val request: SendMessageBatchRequest = new SendMessageBatchRequest(sqsArn, batch.asJava)
    wrapAsyncMethod(sqsClient.sendMessageBatchAsync, request)
  }

  def shard(registrationCount: Int): List[ShardRange] = {
    val shardSpace = -Short.MinValue.toInt + Short.MaxValue.toInt
    val shardCount = Math.ceil(Math.max(1, registrationCount) / BATCH_SIZE.toDouble)
    val step = Math.ceil(shardSpace / shardCount).toInt

    (Short.MinValue until Short.MaxValue by step).toList.map { i =>
      ShardRange(
        start = if (i == Short.MinValue.toInt) Short.MinValue else (i + 1).toShort,
        end = Math.min(i + step, Short.MaxValue).toShort
      )
    }
  }

  def prepareBatch(platform: Platform, notification: Notification, registrationCount: Int): List[SendMessageBatchRequestEntry] = {
    val notificationJson = Json.stringify(Notification.jf.writes(notification))
    shard(registrationCount).map { shard =>
      ShardedNotification(notification, shard)
      val messageId = s"${notification.id}-$platform-[${shard.start},${shard.end}]"
      new SendMessageBatchRequestEntry(messageId, notificationJson)
    }
  }
}
