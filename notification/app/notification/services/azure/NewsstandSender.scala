package notification.services.azure

import java.time.{Clock, Duration, Instant}
import java.util.UUID

import com.gu.notificationschedule.dynamo.{NotificationSchedulePersistenceAsync, NotificationsScheduleEntry}
import models.{NewsstandShardConfig, NewsstandShardNotification}
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

class NewsstandSender(
  legacyNewsstandRegistrationConverterConfig:NewsstandShardConfig,
  notificationSchedulePersistence: NotificationSchedulePersistenceAsync,
  clock: Clock = Clock.systemUTC()
)(implicit executionContext: ExecutionContext) {
  val oneDayInSeconds = Duration.ofDays(1).getSeconds
  def sendNotification(id: UUID): Future[Unit] = {
    val nowSeconds = Instant.now(clock).getEpochSecond
    Future.sequence {
      Range(0, legacyNewsstandRegistrationConverterConfig.shards.toInt).map(registeredShard => {
        val offset = registeredShard + 1
        val shardUuid = new UUID(id.getMostSignificantBits, id.getLeastSignificantBits + offset)
        notificationSchedulePersistence.writeAsync(NotificationsScheduleEntry(
          shardUuid.toString,
          Json.prettyPrint(NewsstandShardNotification.jf.writes(NewsstandShardNotification(shardUuid, registeredShard))),
          nowSeconds + (60L * offset),
          nowSeconds + (60L * offset) + oneDayInSeconds
        ), None).future
      })
    }.map(_ => ())
  }
}