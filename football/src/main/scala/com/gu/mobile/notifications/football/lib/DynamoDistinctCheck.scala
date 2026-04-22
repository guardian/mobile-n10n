package com.gu.mobile.notifications.football.lib

import scala.concurrent.{ExecutionContext, Future}
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.gu.mobile.notifications.client.models.{FootballMatchStatusPayload, Payload}
import org.scanamo.{DynamoFormat, ScanamoAsync, Table}
import DynamoDistinctCheck.{Distinct, DistinctStatus, Duplicate, Unknown}
import com.gu.mobile.notifications.client.models.liveActitivites.LiveActivityPayload
import com.gu.mobile.notifications.football.Logging
import play.api.libs.json.Json

case class DynamoMatchNotification(
  notificationId: String,
  matchId: Option[String],
  notification: String,
  ttl: Long
)

object DynamoMatchNotification {
  def apply[A <: Payload](notification: A)(implicit writes: play.api.libs.json.Writes[A]): DynamoMatchNotification = {
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

case class DynamoMatchLiveActivity(
  id: String,
  liveActivityID: String,
  payload: String,
  ttl: Long
)

object DynamoMatchLiveActivity {
  def apply(payload: LiveActivityPayload): DynamoMatchLiveActivity = {
    DynamoMatchLiveActivity(
      id = payload.id.toString,
      liveActivityID = payload.liveActivityID,
      payload = Json.prettyPrint(Json.toJson(payload)),
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

class DynamoDistinctCheck[A <: Payload, D: DynamoFormat](
  client: AmazonDynamoDBAsync,
  tableName: String,
  partitionKeyName: String,
  toDynamoModel: A => D
) extends Logging {
  def insertNotification(item: A)(implicit ec: ExecutionContext): Future[DistinctStatus] = {
    import org.scanamo.syntax._

    lazy val scanamoAsync: ScanamoAsync = ScanamoAsync(client)
    lazy val notificationsTable = Table[D](tableName)
    val dynamoModel = toDynamoModel(item)

    val putResult = scanamoAsync.exec(notificationsTable.given(not(attributeExists(partitionKeyName))).put(dynamoModel))
    putResult map {
      case Right(_) =>
        logger.info(s"Distinct event ${item.id} written to dynamodb $tableName")
        Distinct
      case Left(error) =>
        logger.info(s"Received $error when attempting to write ${item.id} to dynamodb $tableName, assuming it's a duplicate")
        Duplicate
    } recover {
      case e =>
        logger.error(s"Failure while writing to dynamodb $tableName: ${e.getMessage}.  Request will be retried on next poll")
        Unknown
    }
  }
}
