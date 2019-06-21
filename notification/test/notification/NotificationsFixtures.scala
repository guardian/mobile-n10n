package notification

import java.net.URI
import java.util.UUID

import _root_.models.Importance.Major
import _root_.models.Link.Internal
import _root_.models.TopicTypes.Breaking
import _root_.models._
import _root_.models.TopicTypes.Content
import notification.services.guardian.GuardianFailedToQueueShard
import org.joda.time.DateTime
import play.api.test.FakeRequest

trait NotificationsFixtures {
  def breakingNewsNotification(topics: List[Topic]): BreakingNewsNotification = BreakingNewsNotification(
    id = UUID.fromString("30aac5f5-34bb-4a88-8b69-97f995a4907b"),
    title = "The Guardian",
    message = "Mali hotel attack: UN counts 27 bodies as hostage situation ends",
    thumbnailUrl = Some(new URI("http://media.guim.co.uk/09951387fda453719fe1fee3e5dcea4efa05e4fa/0_181_3596_2160/140.jpg")),
    sender = "test",
    link = Internal("world/live/2015/nov/20/mali-hotel-attack-gunmen-take-hostages-in-bamako-live-updates", None, GITContent),
    imageUrl = Some(new URI("https://mobile.guardianapis.com/img/media/a5fb401022d09b2f624a0cc0484c563fd1b6ad93/" +
      "0_308_4607_2764/master/4607.jpg/6ad3110822bdb2d1d7e8034bcef5dccf?width=800&height=-&quality=85")),
    importance = Major,
    topic = topics,
    dryRun = None
  )

  def newsstandShardNotification() = NewsstandShardNotification(
    id = UUID.fromString("41D80477-E4DE-42AD-B490-AE99951E7F37"),
    shard = 1
  )
  
  def contentTargetedBreakingNewsPush(importance: Importance = Major): Notification = BreakingNewsNotification(
    id = UUID.randomUUID(),
    title = "",
    message = "",
    thumbnailUrl = None,
    sender = "test",
    link = Internal("capiId", None, GITContent),
    imageUrl = None,
    importance = importance,
    topic = List(Topic(Content, "us-presidential-2016")),
    dryRun = None
  )

  val providerError =  GuardianFailedToQueueShard("test", "test")

  val apiKey = "test"
  val authenticatedRequest = FakeRequest(method = "POST", path = "").withHeaders("Authorization" -> s"Bearer $apiKey")
  val invalidAuthenticatedRequest = FakeRequest(method = "POST", path = "").withHeaders( "Authorization" -> s"Bearer wrong-key")
  val validTopics = List(Topic(Breaking, "uk"), Topic(Breaking, "us"))
  val validNewsstandNotificationsTopic = List(Topic(TopicTypes.NewsstandShard, "newsstand-shard-1"))
  val requestWithValidTopics = authenticatedRequest.withBody(breakingNewsNotification(validTopics))

  def senderReport(
    senderName: String,
    platformStats: Option[PlatformStatistics] = None,
    sendersId: Option[String] = None,
    sentTimeOffsetSeconds: Int = 0
  ): SenderReport =
    SenderReport(senderName, DateTime.now.plusSeconds(sentTimeOffsetSeconds), sendersId, platformStats)

  def reportWithSenderReports(reports: List[SenderReport]): DynamoNotificationReport = DynamoNotificationReport.create(
    breakingNewsNotification(validTopics),
    reports = reports,
    version = None
  )
}

