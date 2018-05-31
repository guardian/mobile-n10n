package notification.services.azure

import java.time.{Clock, Instant, ZoneOffset}
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

import azure.apns.{APS, Body, LegacyProperties}
import azure.{APNSRawPush, NotificationHubClient, Tags}
import com.gu.notificationschedule.dynamo.NotificationsScheduleEntry
import models.TopicTypes.Newsstand
import models.{NewsstandShardConfig, Topic}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.mutable.ExecutionEnvironment

import scala.concurrent.{Future, Promise}
import scala.util.Try

class NewsstandSenderTest extends Specification with Mockito with ExecutionEnvironment {
  def is(implicit ee: ExecutionEnv) = {
    "Newsstand sender" should {
      "Send a simple newsstand notification" in {
        val hubClient = mock[NotificationHubClient]
        val sampleHubResult = Right(Some("sample hub result"))
        val randomId = UUID.randomUUID
        val instant = Instant.now()
        val queue= new ConcurrentLinkedQueue[NotificationsScheduleEntry]()
        val sender = new NewsstandSender(
          hubClient,
          NewsstandShardConfig(3),
          (notificationsScheduleEntry: NotificationsScheduleEntry, maybeEpochSentS: Option[Long]) => {
            Try {
              queue.add(notificationsScheduleEntry)
            }.fold(Promise.failed[Unit](_), _ => Promise.successful(()))
          },
          Clock.fixed(instant, ZoneOffset.UTC)
        )
        val rawPush = APNSRawPush(
          body = Body(
            aps = APS(
              alert = None,
              `content-available` = Some(1),
              sound = None
            ),
            customProperties = LegacyProperties(Map.empty)
          ),
          tags = Some(Tags.fromTopics(Set(Topic(Newsstand, "newsstand"))))
        )

        hubClient.sendNotification(rawPush) returns Future(sampleHubResult)

        sender.sendNotification(randomId) must be_==(sampleHubResult).await

        var current: NotificationsScheduleEntry= queue.poll()

        var notificationsScheduleEntries = Set[NotificationsScheduleEntry]()
        while (current != null) {
          notificationsScheduleEntries = notificationsScheduleEntries + current
          current = queue.poll()
        }
        val nowSeconds = instant.getEpochSecond
        val sevenDaysFromNowSeconds = nowSeconds + 604800
        val uuidMsb = randomId.getMostSignificantBits
        val uuidLsb = randomId.getLeastSignificantBits
        notificationsScheduleEntries must be equalTo Set(
          NotificationsScheduleEntry(
            new UUID(uuidMsb, uuidLsb + 1).toString,
            1.toString,
            nowSeconds + 60,
            sevenDaysFromNowSeconds + 60
          ), NotificationsScheduleEntry(
            new UUID(uuidMsb, uuidLsb + 2).toString,
            2.toString ,
            nowSeconds + 120,
            sevenDaysFromNowSeconds  + 120
          ), NotificationsScheduleEntry(
            new UUID(uuidMsb, uuidLsb + 3).toString,
            3.toString,
            nowSeconds + 180,
            sevenDaysFromNowSeconds + 180
          ))
        there was one(hubClient).sendNotification(rawPush)
      }
    }
  }
}
