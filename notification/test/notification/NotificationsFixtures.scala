package notification

import java.net.URI
import java.util.UUID

import _root_.models.Importance.Major
import _root_.models.Link.Internal
import _root_.models.NotificationType.BreakingNews
import _root_.models.TopicTypes.Breaking
import _root_.models.{NotificationStatistics, NotificationReport, BreakingNewsNotification, Notification, Topic}
import org.joda.time.DateTime
import play.api.test.FakeRequest
import tracking.TrackingError

trait NotificationsFixtures {
  def breakingNewsNotification(topics: Set[Topic]): Notification = BreakingNewsNotification(
    id = UUID.fromString("30aac5f5-34bb-4a88-8b69-97f995a4907b"),
    title = "The Guardian",
    message = "Mali hotel attack: UN counts 27 bodies as hostage situation ends",
    thumbnailUrl = Some(new URI("http://media.guim.co.uk/09951387fda453719fe1fee3e5dcea4efa05e4fa/0_181_3596_2160/140.jpg")),
    sender = "test",
    link = Internal("world/live/2015/nov/20/mali-hotel-attack-gunmen-take-hostages-in-bamako-live-updates"),
    imageUrl = Some(new URI("https://mobile.guardianapis.com/img/media/a5fb401022d09b2f624a0cc0484c563fd1b6ad93/" +
                              "0_308_4607_2764/master/4607.jpg/6ad3110822bdb2d1d7e8034bcef5dccf?width=800&height=-&quality=85")),
    importance = Major,
    topic = topics
  )

  def trackingError = new TrackingError {
    override def reason: String = "tracker problem"
  }


  val apiKey = "test"
  val authenticatedRequest = FakeRequest(method = "POST", path = s"?api-key=$apiKey")
  val validTopics = Set(Topic(Breaking, "uk"), Topic(Breaking, "us"))
  val requestWithValidTopics = authenticatedRequest.withBody(breakingNewsNotification(validTopics))

  val notificationReport = NotificationReport(
    UUID.randomUUID(),
    BreakingNews,
    breakingNewsNotification(validTopics),
    sentTime = DateTime.now().minusSeconds(2),
    statistics = NotificationStatistics(Map.empty)
  )
}

