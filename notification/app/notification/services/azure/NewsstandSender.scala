package notification.services.azure

import java.time.{Clock, Duration, Instant}
import java.util.UUID

import azure.{APNSRawPush, NotificationHubClient, Tags}
import com.gu.notificationschedule.dynamo.{NotificationSchedulePersistenceAsync, NotificationsScheduleEntry}
import models.{NewsstandShardConfig, NewsstandShardNotification, Topic}
import models.TopicTypes.Newsstand
import notification.models.ios.NewsstandNotification
import play.api.libs.json.Json

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}


class NewsstandSender(
                       hubClient: NotificationHubClient,
                       legacyNewsstandRegistrationConverterConfig:NewsstandShardConfig,
                       notificationSchedulePersistence: NotificationSchedulePersistenceAsync,
                       clock: Clock = Clock.systemUTC()
                     )(implicit executionContext: ExecutionContext) {
  val sevenDaysInSeconds = Duration.ofDays(7).getSeconds
  def sendNotification(id: UUID) = {
    val push = APNSRawPush(
      body = NewsstandNotification(id).payload,
      tags = Some(Tags.fromTopics(Set(Topic(Newsstand, "newsstand"))))
    )
    hubClient.sendNotification(push).flatMap(hubResult => {
      scheduleShards(id).map(_ => hubResult)
    })
  }

  private def scheduleShards (id: UUID): Future[immutable.IndexedSeq[Unit]] = {
    val nowSeconds = Instant.now(clock).getEpochSecond
    Future.sequence {
      Range(0, legacyNewsstandRegistrationConverterConfig.shards.toInt).map(registeredShard => {
        val offset = registeredShard + 1
        val shardUuid = new UUID(id.getMostSignificantBits, id.getLeastSignificantBits + offset)
        notificationSchedulePersistence.writeAsync(NotificationsScheduleEntry(
          shardUuid.toString,
          Json.prettyPrint(NewsstandShardNotification.jf.writes(NewsstandShardNotification(shardUuid, registeredShard))),
          nowSeconds + (60L * offset),
          nowSeconds + (60L * offset) + sevenDaysInSeconds
        ), None).future
      })
    }
  }
}