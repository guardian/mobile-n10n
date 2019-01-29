package notification

import java.net.URI
import java.util.UUID

import _root_.models.Importance.Major
import _root_.models.Link.Internal
import _root_.models.TopicTypes.Breaking
import _root_.models._
import _root_.models.TopicTypes.ElectionResults
import _root_.models.elections
import notification.models.Push
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
    topic = topics
  )

  def electionNotification(importance: Importance) = ElectionNotification(
    id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
    message = "• 270 electoral votes needed to win\n• 35 states called, 5 swing states (OH, PA, NV, CO, FL)\n• Popular vote: Clinton 52%, Trump 43% with 42% precincts reporting",
    shortMessage = Some(""),
    expandedMessage = Some(""),
    sender = "some-sender",
    title = "Live election results",
    importance = importance,
    link = Internal("us", Some("https://gu.com/p/4p7xt"), GITContent),
    resultsLink = Internal("us", Some("https://gu.com/p/2zzz"), GITContent),
    results = elections.ElectionResults(List(
      elections.CandidateResults(
        name = "Clinton",
        states = List.empty,
        electoralVotes = 220,
        popularVotes = 5000000,
        avatar = Some(new URI("http://e4775a29.ngrok.io/clinton-neutral.png")),
        color = "#005689"
      ),
      elections.CandidateResults(
        name = "Trump",
        states = List.empty,
        electoralVotes = 133,
        popularVotes = 5000000,
        avatar = Some(new URI("http://e4775a29.ngrok.io/trump-neutral.png")),
        color = "#d61d00"
      )
    )),
    topic = List(Topic(ElectionResults, "us-presidential-2016"))
  )

  def newsstandShardNotification() = NewsstandShardNotification(
    id = UUID.fromString("41D80477-E4DE-42AD-B490-AE99951E7F37"),
    shard = 1
  )
  
  def electionTargetedBreakingNewsPush(importance: Importance = Major): Push = Push(
    notification = BreakingNewsNotification(
      id = UUID.randomUUID(),
      title = "",
      message = "",
      thumbnailUrl = None,
      sender = "test",
      link = Internal("capiId", None, GITContent),
      imageUrl = None,
      importance = importance,
      topic = List()
    ),
    destination = Set(Topic(ElectionResults, "us-presidential-2016"))
  )

  def topicTargetedBreakingNewsPush(notification: Notification): Push = Push(
    notification = notification,
    destination = notification.topic.toSet
  )

  val providerError =  GuardianFailedToQueueShard("test", "test")

  val apiKey = "test"
  val electionsApiKey = "elections-test"
  //val authenticatedRequest = FakeRequest(method = "POST", path = s"?api-key=$apiKey")
  val authenticatedRequest = FakeRequest(method = "POST", path = "").withHeaders("Authorization" -> s"Bearer $apiKey")
  val electionsAuthenticatedRequest = FakeRequest(method = "POST", path = "").withHeaders( "Authorization" -> s"Bearer $electionsApiKey")
  val invalidAuthenticatedRequest = FakeRequest(method = "POST", path = "").withHeaders( "Authorization" -> s"Bearer wrong-key")
  val validTopics = List(Topic(Breaking, "uk"), Topic(Breaking, "us"))
  val validElectionTopics = List(Topic(ElectionResults, "uk"), Topic(ElectionResults, "us"))
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

