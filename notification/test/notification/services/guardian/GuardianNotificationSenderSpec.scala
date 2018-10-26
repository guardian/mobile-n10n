package notification.services.guardian

import java.net.URI
import java.util.UUID

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.{BatchResultErrorEntry, SendMessageBatchRequest, SendMessageBatchResult}
import models.Importance.Major
import models.Link.Internal
import models.TopicTypes.Breaking
import models._
import notification.models.Push
import notification.services.SenderError
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.duration.DurationLong
import scala.concurrent.{Await, Future}
import org.apache.commons.lang3.concurrent.ConcurrentUtils

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
      val futureResult = notificationSender.sendNotification(Push(notification, Set()))
      val result = Await.result(futureResult, 10.seconds)

      there was one(sqsClient).sendMessageBatchAsync(any[SendMessageBatchRequest], any[AsyncHandler[SendMessageBatchRequest, SendMessageBatchResult]])

      result should beRight.which { senderReport =>
        senderReport.senderName shouldEqual "Guardian"
        senderReport.sendersId should beNone
        senderReport.platformStatistics should beSome(PlatformStatistics(iOS, 1))
      }
    }

    "put many batches messages on the queue for popular topics" in new GuardianNotificationSenderScope(registrationCount = 3000000) {
      val futureResult = notificationSender.sendNotification(Push(notification, Set()))
      val result = Await.result(futureResult, 10.seconds)

      there was two(sqsClient).sendMessageBatchAsync(any[SendMessageBatchRequest], any[AsyncHandler[SendMessageBatchRequest, SendMessageBatchResult]])

      result should beRight.which { senderReport =>
        senderReport.senderName shouldEqual "Guardian"
        senderReport.sendersId should beNone
        senderReport.platformStatistics should beSome(PlatformStatistics(iOS, 3000000))
      }
    }

    "put many batches messages on the queue, even if the topic counter fails" in new GuardianNotificationSenderScope(registrationCount = 2000000) {

      override val notificationSender = new GuardianNotificationSender(
        sqsClient = sqsClient,
        registrationCounter = new TopicRegistrationCounter {
          override def count(topics: List[Topic]): Future[PlatformCount] = Future.failed(new RuntimeException("exception"))
        },
        platform = iOS,
        sqsArn = ""
      )

      val futureResult = notificationSender.sendNotification(Push(notification, Set()))
      val result = Await.result(futureResult, 10.seconds)

      there was atLeast(1)(sqsClient).sendMessageBatchAsync(any[SendMessageBatchRequest], any[AsyncHandler[SendMessageBatchRequest, SendMessageBatchResult]])

      result should beRight.which { senderReport =>
        senderReport.senderName shouldEqual "Guardian"
        senderReport.sendersId should beNone
      }
    }

    "return an error if one of the batches couldn't be pushed to the queue" in new GuardianNotificationSenderScope(
      sendMessageBatchResult = new SendMessageBatchResult().withFailed(
        new BatchResultErrorEntry().withCode("123").withId("456").withMessage("error")
      )
    ) {
      val futureResult = notificationSender.sendNotification(Push(notification, Set()))
      val result = Await.result(futureResult, 10.seconds)

      there was one(sqsClient).sendMessageBatchAsync(any[SendMessageBatchRequest], any[AsyncHandler[SendMessageBatchRequest, SendMessageBatchResult]])

      result should beLeft(GuardianFailedToQueueShard(
        senderName = s"Guardian ios",
        reason = s"Unable to queue notification. Please check the logs for notification 4c261110-4672-4451-a5b8-3422c6839c42"
      ): SenderError)
    }
  }

  class GuardianNotificationSenderScope(
    registrationCount: Int = 1,
    sendMessageBatchResult: SendMessageBatchResult = new SendMessageBatchResult()
  ) extends Scope {

    val notification = BreakingNewsNotification(
      id = UUID.fromString("4c261110-4672-4451-a5b8-3422c6839c42"),
      title = "Test notification",
      message = "The message",
      thumbnailUrl = Some(new URI("https://invalid.url/img.png")),
      sender = "UnitTests",
      link = Internal("some/capi/id", None, GITContent),
      imageUrl = Some(new URI("https://invalid.url/img.png")),
      importance = Major,
      topic = List(Topic(`type` = Breaking, name = "uk"))
    )

    def topicStats(registrationCount: Int): PlatformCount = PlatformCount(
      total = registrationCount * 2,
      ios = registrationCount,
      android = registrationCount,
      newsstand = 0
    )

    val sqsClient = {
      val s = mock[AmazonSQSAsync]
      s.sendMessageBatchAsync(
        any[SendMessageBatchRequest],
        any[AsyncHandler[SendMessageBatchRequest, SendMessageBatchResult]]
      ) answers { p =>
        val params = p.asInstanceOf[Array[Object]]
        val handler = params(1).asInstanceOf[AsyncHandler[SendMessageBatchRequest, SendMessageBatchResult]]
        handler.onSuccess(params(0).asInstanceOf[SendMessageBatchRequest], sendMessageBatchResult)
        ConcurrentUtils.constantFuture(sendMessageBatchResult)
      }
      s
    }

    val notificationSender = new GuardianNotificationSender(
      sqsClient = sqsClient,
      registrationCounter = new TopicRegistrationCounter {
        override def count(topics: List[Topic]): Future[PlatformCount] = Future.successful(topicStats(registrationCount))
      },
      platform = iOS,
      sqsArn = ""
    )
  }
}
