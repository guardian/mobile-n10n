package football.lib

import scala.concurrent.{ExecutionContext, Future}
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.gu.mobile.notifications.client.models.{FootballMatchStatusPayload, NotificationPayload}
import football.Logging
import org.scanamo.{ScanamoAsync, Table}
import football.lib.DynamoDistinctCheck.{Distinct, DistinctStatus, Duplicate, Unknown}
import play.api.libs.json.Json

case class DynamoMatchNotification(
  notificationId: String,
  matchId: Option[String],
  notification: String,
  ttl: Long
)

object DynamoMatchNotification {
  def apply(notification: NotificationPayload): DynamoMatchNotification = {
    val matchId = PartialFunction.condOpt(notification) {
      case p: FootballMatchStatusPayload => p.matchId
    }

    DynamoMatchNotification(
      notificationId = notification.id.toString,
      matchId = matchId,
      notification = Json.prettyPrint(Json.toJson(notification)),
      ttl = (System.currentTimeMillis() / 1000) + (14 * 24 * 3600)
    )
  }
}

object DynamoDistinctCheck {
  sealed trait DistinctStatus
  case object Distinct extends DistinctStatus
  case object Duplicate extends DistinctStatus
  case object Unknown extends DistinctStatus
}

class DynamoDistinctCheck(client: AmazonDynamoDBAsync, tableName: String) extends Logging {
  def insertNotification(notification: NotificationPayload)(implicit ec: ExecutionContext): Future[DistinctStatus] = {
    import org.scanamo.syntax._
    import org.scanamo.auto._

    lazy val scanamoAsync: ScanamoAsync = ScanamoAsync(client)
    lazy val notificationsTable = Table[DynamoMatchNotification](tableName)

    val putResult = scanamoAsync.exec(notificationsTable.given(not(attributeExists("notificationId"))).put(DynamoMatchNotification(notification)))
    putResult map {
      case Right(_) =>
        logger.info(s"Distinct notification ${notification.id} written to dynamodb")
        Distinct
      case Left(_) =>
        logger.debug(s"Notification ${notification.id} already exists in dynamodb - discarding")
        Duplicate
    } recover {
      case e =>
        logger.error(s"Failure while writing to dynamodb: ${e.getMessage}.  Request will be retried on next poll")
        Unknown
    }
  }
}
