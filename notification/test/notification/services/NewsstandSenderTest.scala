package notification.services

import java.time.{Clock, Instant, ZoneOffset}
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

import com.gu.notificationschedule.dynamo.NotificationsScheduleEntry
import models.{NewsstandShardConfig, NewsstandShardNotification, Notification, Topic}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.mutable.ExecutionEnvironment
import play.api.libs.json.Json

import scala.concurrent.Promise
import scala.util.Try

class NewsstandSenderTest extends Specification with Mockito with ExecutionEnvironment {
  def is(implicit ee: ExecutionEnv) = {
    "Newsstand sender" should {
      "Send a simple newsstand notification" in {
        val randomId = UUID.randomUUID
        val instant = Instant.now()
        val queue= new ConcurrentLinkedQueue[NotificationsScheduleEntry]()
        val sender = new NewsstandSender(
          NewsstandShardConfig(3),
          (notificationsScheduleEntry: NotificationsScheduleEntry, maybeEpochSentS: Option[Long]) => {
            Try {
              queue.add(notificationsScheduleEntry)
            }.fold(Promise.failed[Unit](_), _ => Promise.successful(()))
          },
          Clock.fixed(instant, ZoneOffset.UTC)
        )

        sender.sendNotification(randomId) must be_==(()).await

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
        val firstUuid = new UUID(uuidMsb, uuidLsb + 1)
        val secondUuid = new UUID(uuidMsb, uuidLsb + 2)
        val thirdUuid = new UUID(uuidMsb, uuidLsb + 3)
        notificationsScheduleEntries.map(_.copy(notification = "")) must be equalTo Set(
          NotificationsScheduleEntry(
            firstUuid.toString,
            "",
            nowSeconds + 60,
            sevenDaysFromNowSeconds + 60
          ), NotificationsScheduleEntry(
            secondUuid.toString,
            "",
            nowSeconds + 120,
            sevenDaysFromNowSeconds  + 120
          ), NotificationsScheduleEntry(
            thirdUuid.toString,
            "",
            nowSeconds + 180,
            sevenDaysFromNowSeconds + 180
          ))

        notificationsScheduleEntries.map(_.notification).map(jsonString => Notification.jf.reads(Json.parse(jsonString)).get) must be equalTo Set(
          NewsstandShardNotification(firstUuid, 0),
          NewsstandShardNotification(secondUuid, 1),
          NewsstandShardNotification(thirdUuid, 2)
        )
      }
    }
  }
}
