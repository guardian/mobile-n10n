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
import scala.util.control.NonFatal

case class GuardianFailedToQueueShard(
  senderName: String,
  reason: String
) extends SenderError

class GuardianNotificationSender(
  sqsClient: AmazonSQSAsync,
  registrationCounter: TopicRegistrationCounter,
  platform: Platform,
  sqsUrl: String,
)(implicit ec: ExecutionContext) extends NotificationSender {

  val WORKER_BATCH_SIZE: Int = 10000
  val SQS_BATCH_SIZE: Int = 10

  private val logger: Logger = Logger.apply(classOf[GuardianNotificationSender])

  override def sendNotification(push: Push): Future[SenderResult] = {
    val result = for {
      registrationCount <- countRegistration(platform, push.notification.topic)
      workerBatches = prepareBatch(platform, push.notification, registrationCount)
      sqsBatchResults <- sendBatch(workerBatches)
    } yield {
      val failed = sqsBatchResults.flatMap(response => Option(response.getFailed).map(_.asScala.toList).getOrElse(Nil))
      if (failed.isEmpty) {
        Right(SenderReport(
          Guardian.value,
          DateTime.now,
          None,
          registrationCount.map { count => PlatformStatistics(
            platform,
            count
          )}
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

    result.recover {
      case NonFatal(e) =>
        logger.error(e.getMessage, e)
        Left(GuardianFailedToQueueShard(
          senderName = s"Guardian $platform",
          reason = s"Unable to send notification, got an exception ${e.getMessage}"
        ))
    }
  }

  private def countRegistration(platform: Platform, topics: List[Topic]): Future[Option[Int]] = {
    // in case of an exception when calling registrationCounter, we want to continue anyway
    registrationCounter.count(topics).map(platformCount => Some(platformCount.get(platform))).recover {
      case NonFatal(e) =>
        logger.error("Unable to count registration for a list of topics", e)
        None
    }
  }

  def sendBatch(workerBatches: List[SendMessageBatchRequestEntry]): Future[List[SendMessageBatchResult]] = {
    val sqsBatches = workerBatches.grouped(SQS_BATCH_SIZE).toList
    Future.traverse(sqsBatches) { sqsBatch =>
      val request: SendMessageBatchRequest = new SendMessageBatchRequest(sqsUrl, sqsBatch.asJava)
      wrapAsyncMethod(sqsClient.sendMessageBatchAsync, request)
    }
  }

  def shard(registrationCount: Int): List[ShardRange] = {
    val shardSpace = -Short.MinValue.toInt + Short.MaxValue.toInt
    val shardCount = Math.ceil(Math.max(1, registrationCount) / WORKER_BATCH_SIZE.toDouble)
    val step = Math.ceil(shardSpace / shardCount).toInt

    (Short.MinValue until Short.MaxValue by step).toList.map { i =>
      ShardRange(
        start = if (i == Short.MinValue.toInt) Short.MinValue else (i + 1).toShort,
        end = Math.min(i + step, Short.MaxValue).toShort
      )
    }
  }

  def prepareBatch(platform: Platform, notification: Notification, registrationCount: Option[Int]): List[SendMessageBatchRequestEntry] = {
    val countWithDefault: Int = registrationCount match {
      case Some(count) => count
      case None if notification.topic.exists(_.`type` == TopicTypes.Breaking) && platform != Newsstand =>
        logger.error("Unable to count registration for a list of topics during a breaking news, falling back on 1.5M")
        1500000 // fallback on 1.5M: the worst case
      case None =>
        logger.error("Unable to count registration for a list of topics, falling back on 1")
        1
    }

    shard(countWithDefault).map { shard =>
      ShardedNotification(notification, shard)
      val shardedNotification = ShardedNotification(notification, shard)
      val payloadJson = Json.stringify(Json.toJson(shardedNotification))
      val messageId = s"${notification.id}-$platform-${shard.start}-${shard.end}"
      new SendMessageBatchRequestEntry(messageId, payloadJson)
    }
  }
}
