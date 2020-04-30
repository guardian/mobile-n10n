package notification.services.guardian

import com.amazonaws.services.sqs.AmazonSQSAsync
import notification.services.{NotificationSender, SenderError, SenderResult}
import aws.AWSAsync._
import com.amazonaws.services.sqs.model.{SendMessageBatchRequest, SendMessageBatchRequestEntry, SendMessageBatchResult}
import models.NotificationType.BreakingNews
import models.Provider.Guardian
import models._
import org.joda.time.DateTime
import play.api.libs.json.Json

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import models.TopicCount.topicCountJF
import org.slf4j.{Logger, LoggerFactory}

case class GuardianFailedToQueueShard(
  senderName: String,
  reason: String
) extends SenderError

class GuardianNotificationSender(
  sqsClient: AmazonSQSAsync,
  registrationCounter: TopicRegistrationCounter,
  harvesterSqsUrl: String,
)(implicit ec: ExecutionContext) extends NotificationSender {

  val WORKER_BATCH_SIZE: Int = 10000
  val SQS_BATCH_SIZE: Int = 10

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def shouldSendToHarveseter(notification: Notification) = true

  override def sendNotification(notification: Notification): Future[SenderResult] = {
    val result = for {
      registrationCount <- countRegistration(notification.topic)
      workerBatches = prepareBatch(notification, registrationCount)
      sqsBatchResults <- sendBatch(workerBatches, harvesterSqsUrl)
    } yield {
      val failed = sqsBatchResults.flatMap(response => Option(response.getFailed).map(_.asScala.toList).getOrElse(Nil))
      if (failed.isEmpty) {
        Right(SenderReport(
          senderName = Guardian.value,
          sentTime = DateTime.now,
          sendersId = None,
          platformStatistics = None,
          totalRegistrationCount = registrationCount
        ))
      } else {
        failed.foreach { failure =>
          logger.error(s"Unable to queue notification ${notification.id}: " +
            s"${failure.getId} - ${failure.getCode} - ${failure.getMessage}")
        }
        Left(GuardianFailedToQueueShard(
          senderName = s"Guardian",
          reason = s"Unable to queue notification. Please check the logs for notification ${notification.id}")
        )
      }
    }

    result.recover {
      case NonFatal(e) =>
        logger.error(e.getMessage, e)
        Left(GuardianFailedToQueueShard(
          senderName = s"Guardian",
          reason = s"Unable to send notification, got an exception ${e.getMessage}"
        ))
    }
  }

  private def countRegistration(topics: List[Topic]): Future[Option[Int]] = {
    // in case of an exception when calling registrationCounter, we want to continue anyway
    registrationCounter.count(topics).map(platformCount => Some(platformCount)).recover {
      case NonFatal(e) =>
        logger.error("Unable to count registration for a list of topics", e)
        None
    }
  }

  def sendBatch(workerBatches: List[SendMessageBatchRequestEntry], sqsUrl: String): Future[List[SendMessageBatchResult]] = {
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

  def prepareBatch(notification: Notification, registrationCount: Option[Int]): List[SendMessageBatchRequestEntry] = {
    val countWithDefault: Int = registrationCount match {
      case Some(count) => count
      case None if notification.topic.exists(_.`type` == TopicTypes.Breaking) =>
        logger.error("Unable to count registration for a list of topics during a breaking news, falling back on 1.5M")
        1500000 // fallback on 1.5M: the worst case
      case None =>
        logger.error("Unable to count registration for a list of topics, falling back on 1")
        1
    }

    shard(countWithDefault).map { shard =>
      val shardedNotification = ShardedNotification(notification, shard)
      val payloadJson = Json.stringify(Json.toJson(shardedNotification))
      val messageId = s"${notification.id}-${shard.start}-${shard.end}"
      new SendMessageBatchRequestEntry(messageId, payloadJson)
    }
  }
}
