package notification.services.azure

import java.time.{Clock, Duration, Instant}
import java.util.UUID

import azure.{APNSRawPush, NotificationHubClient, Tags}
import com.gu.notificationschedule.dynamo.{NotificationSchedulePersistenceAsync, NotificationsScheduleEntry}
import models.{NewsstandShardConfig, Topic}
import models.TopicTypes.Newsstand
import notification.models.ios.NewsstandNotification

import scala.concurrent.{ExecutionContext, Future}


class NewsstandSender(
                       hubClient: NotificationHubClient,
                       legacyNewsstandRegistrationConverterConfig:NewsstandShardConfig,
                       notificationSchedulePersistence: NotificationSchedulePersistenceAsync,
                       clock: Clock = Clock.systemUTC()
                     ) {
  implicit  val executionContext:ExecutionContext = ExecutionContext.Implicits.global
  val sevenDaysInSeconds = Duration.ofDays(7).getSeconds
  def sendNotification(id: UUID) = {
    val push = APNSRawPush(
      body = NewsstandNotification(id).payload,
      tags = Some(Tags.fromTopics(Set(Topic(Newsstand, "newsstand"))))
    )
    val shards = legacyNewsstandRegistrationConverterConfig.shards
    val nowSeconds = Instant.now(clock).getEpochSecond

    hubClient.sendNotification(push).flatMap(hubResult => {
      Future.sequence{Range(1,shards.toInt + 1).map(shard => notificationSchedulePersistence.writeAsync(NotificationsScheduleEntry(
        new UUID(id.getMostSignificantBits, id.getLeastSignificantBits+shard).toString,
        shard.toString,
        nowSeconds + (60L * shard),
        nowSeconds + (60L * shard) + sevenDaysInSeconds
      ), None).future)}.map(_ => hubResult)
    })
  }
}