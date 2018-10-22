package notification.services.guardian

import java.net.URI
import java.util.UUID

import com.amazonaws.services.sqs.AmazonSQSAsync
import models.Importance.Major
import models.Link.Internal
import models.TopicTypes.Breaking
import models._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.Future

class GuardianNotificationSenderSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {
  "GuardianNotificationSender" should {
    "prepare shards with inclusive bounds" in new GuardianNotificationSenderScope {
      val bs = notificationSender.BATCH_SIZE
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
  }

  trait GuardianNotificationSenderScope extends Scope {

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

    def topicStats(registrationCount: Int): TopicStats = TopicStats(Map(
      iOS -> registrationCount,
      Android -> registrationCount,
      Newsstand -> registrationCount
    ))

    val notificationSender = new GuardianNotificationSender(
      sqsClient = mock[AmazonSQSAsync],
      registrationCounter = new TopicRegistrationCounter {
        override def count(topics: List[Topic]): Future[TopicStats] = Future.successful(topicStats(1))
      },
      platform = iOS,
      sqsArn = ""
    )
  }
}
