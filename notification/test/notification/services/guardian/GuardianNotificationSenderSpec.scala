package notification.services.guardian

import java.net.URI
import java.util.UUID
import java.util.concurrent.CompletableFuture
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{BatchResultErrorEntry, SendMessageBatchRequest, SendMessageBatchResponse}
import models.Importance.Major
import models.Link.Internal
import models.TopicTypes.Breaking
import models._
import notification.services.SenderError
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import models.TopicCount.topicCountJF

import scala.concurrent.duration.DurationLong
import scala.concurrent.{Await, Future}
import play.api.libs.json.Format

import java.time.Instant

class GuardianNotificationSenderSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {
  "GuardianNotificationSender" should {
    "prepare shards with inclusive bounds" in new GuardianNotificationSenderScope {
      val bs = notificationSender.WORKER_BATCH_SIZE
      notificationSender.shard(0) shouldEqual List(ShardRange(Short.MinValue, Short.MaxValue))
      notificationSender.shard(1) shouldEqual List(ShardRange(Short.MinValue, Short.MaxValue))
      notificationSender.shard(2) shouldEqual List(ShardRange(Short.MinValue, Short.MaxValue))
      notificationSender.shard(bs - 1) shouldEqual List(ShardRange(Short.MinValue, Short.MaxValue))
      notificationSender.shard(bs) shouldEqual List(ShardRange(Short.MinValue, Short.MaxValue))
      notificationSender.shard(bs + 1) shouldEqual List(ShardRange(Short.MinValue, 0), ShardRange(1, Short.MaxValue))
      notificationSender.shard(bs + 20) shouldEqual List(ShardRange(Short.MinValue, 0), ShardRange(1, Short.MaxValue))
      notificationSender.shard(2 * bs -1) shouldEqual List(ShardRange(Short.MinValue, 0), ShardRange(1, Short.MaxValue))
      notificationSender.shard(2 * bs) shouldEqual List(ShardRange(Short.MinValue, 0), ShardRange(1, Short.MaxValue))
      notificationSender.shard(2 * bs + 1) shouldEqual List(
        ShardRange(Short.MinValue, -10923),
        ShardRange(-10922, 10922),
        ShardRange(10923, Short.MaxValue)
      )
      notificationSender.shard(3 * bs + 20) shouldEqual List(
        ShardRange(Short.MinValue,-16384),
        ShardRange(-16383,0),
        ShardRange(1,16384),
        ShardRange(16385, Short.MaxValue)
      )
    }

    "put batches messages on the queue" in new GuardianNotificationSenderScope {
      val futureResult = notificationSender.sendNotification(notification, Instant.now())
      val result = Await.result(futureResult, 10.seconds)

      there was one(sqsClient).sendMessageBatch(any[SendMessageBatchRequest])

      result should beRight.which { senderReport =>
        senderReport.senderName shouldEqual "Guardian"
        senderReport.sendersId should beNone
        senderReport.totalRegistrationCount should beSome(1)
      }
    }

    "put many batches messages on the queue for popular topics" in new GuardianNotificationSenderScope(registrationCountPerPlatform = 3000000) {
      val futureResult = notificationSender.sendNotification(notification, Instant.now())
      val result = Await.result(futureResult, 10.seconds)

      there was exactly(30)(sqsClient).sendMessageBatch(any[SendMessageBatchRequest])

      result should beRight.which { senderReport =>
        senderReport.senderName shouldEqual "Guardian"
        senderReport.sendersId should beNone
        senderReport.totalRegistrationCount should beSome(3000000)
      }
    }

    "put many batches messages on the queue, even if the topic counter fails" in new GuardianNotificationSenderScope(registrationCountPerPlatform = 2000000) {

      override val notificationSender = new GuardianNotificationSender(
        sqsClient = sqsClient,
        registrationCounter = new TopicRegistrationCounter {
          override def count(topics: List[Topic])(implicit format: Format[TopicCount]): Future[Int] = Future.failed(new RuntimeException("exception"))
        },
        harvesterSqsUrl = ""
      )

      val futureResult = notificationSender.sendNotification(notification, Instant.now())
      val result = Await.result(futureResult, 10.seconds)

      there was atLeast(1)(sqsClient).sendMessageBatch(any[SendMessageBatchRequest])

      result should beRight.which { senderReport =>
        senderReport.senderName shouldEqual "Guardian"
        senderReport.sendersId should beNone
      }
    }

    "return an error if one of the batches couldn't be pushed to the queue" in new GuardianNotificationSenderScope(
      sendMessageBatchResult = SendMessageBatchResponse.builder().failed(
        BatchResultErrorEntry.builder().code("123").id("456").message("error").build()
      ).build()
    ) {
      val futureResult = notificationSender.sendNotification(notification, Instant.now())
      val result = Await.result(futureResult, 10.seconds)

      there was one(sqsClient).sendMessageBatch(any[SendMessageBatchRequest])

      result should beLeft(GuardianFailedToQueueShard(
        senderName = s"Guardian",
        reason = s"Unable to queue notification. Please check the logs for notification 4c261110-4672-4451-a5b8-3422c6839c42"
      ): SenderError)
    }
  }

  class GuardianNotificationSenderScope(
    registrationCountPerPlatform: Int = 1,
    sendMessageBatchResult: SendMessageBatchResponse = SendMessageBatchResponse.builder().build()
  ) extends Scope {

    val notification = BreakingNewsNotification(
      id = UUID.fromString("4c261110-4672-4451-a5b8-3422c6839c42"),
      title = Some("Test notification"),
      message = Some("The message"),
      thumbnailUrl = Some(new URI("https://invalid.url/img.png")),
      sender = "UnitTests",
      link = Internal("some/capi/id", None, GITContent, None),
      imageUrl = Some(new URI("https://invalid.url/img.png")),
      importance = Major,
      topic = List(Topic(`type` = Breaking, name = "uk")),
      dryRun = None
    )

    val sqsClient = {
      val s = mock[SqsAsyncClient]
      s.sendMessageBatch(any[SendMessageBatchRequest]) returns CompletableFuture.completedFuture(sendMessageBatchResult)
      s
    }

    val notificationSender = new GuardianNotificationSender(
      sqsClient = sqsClient,
      registrationCounter = new TopicRegistrationCounter {
        override def count(topics: List[Topic])(implicit format: Format[TopicCount] ): Future[Int] = Future.successful(registrationCountPerPlatform)
      },
      harvesterSqsUrl = ""
    )
  }
}
