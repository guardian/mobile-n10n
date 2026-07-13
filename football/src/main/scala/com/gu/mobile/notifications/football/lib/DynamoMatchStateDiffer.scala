package com.gu.mobile.notifications.football.lib

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.gu.mobile.notifications.client.models.liveActitivites.FootballContentJsonFormats._
import com.gu.mobile.notifications.client.models.liveActitivites.FootballMatchContentState
import com.gu.mobile.notifications.football.Logging
import com.gu.mobile.notifications.football.lib.DynamoDistinctCheck.{Distinct, DistinctStatus, Duplicate, Unknown}
import org.scanamo.{DynamoFormat, ScanamoAsync, Table}
import org.scanamo.generic.auto._
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

case class DynamoMatchState(
  matchId: String,
  matchContentState: FootballMatchContentState,
  lastUpdatedAt: Long,
  ttl: Long
)
object DynamoMatchState {
  def apply(matchId: String, contentState: FootballMatchContentState): DynamoMatchState = {
    DynamoMatchState(
      matchId = matchId,
      matchContentState = contentState,
      lastUpdatedAt = System.currentTimeMillis(),
      ttl = (System.currentTimeMillis() / 1000) + (14 * 24 * 3600)
    )
  }
}

class DynamoMatchStateDiffer(
  client: AmazonDynamoDBAsync,
  val tableName: String,
  partitionKeyName: String,
) extends Logging {

  def isIdentical(id: String, state: FootballMatchContentState)(implicit ec: ExecutionContext): Future[Boolean] = {
    import org.scanamo.syntax._
    import DynamoMatchStateDiffer.footballMatchContentStateDynamoFormat

    lazy val scanamoAsync: ScanamoAsync = ScanamoAsync(client)
    lazy val matchStateTable = Table[DynamoMatchState](tableName)

   scanamoAsync.exec(matchStateTable.get(partitionKeyName -> id)).map {
     // todo test equality is working as expected, especially for the sealed trait hierarchy in FootballMatchContentState
      case Some(Right(existingState)) => existingState.matchContentState == state
      case _ => false
    } recover {
      case e =>
        logger.error(s"Failure while checking for dynamodb match state $tableName: ${e.getMessage}.")
        false
    }
  }

  def updateState(id: String, state: FootballMatchContentState)(implicit ec: ExecutionContext): Future[Unit] = {
    lazy val scanamoAsync: ScanamoAsync = ScanamoAsync(client)
    lazy val matchStateTable = Table[DynamoMatchState](tableName)

    val dynamoPayload = DynamoMatchState(id, state)

    scanamoAsync.exec(matchStateTable.put(dynamoPayload)).map { _ =>
      logger.info(s"New content state for match id $id written to dynamodb $tableName")
    } recover {
      case e =>
        logger.error(s"Failure while writing match state for $id to dynamodb $tableName: ${e.getMessage}")
        throw e // rethrow so the caller's try/Await sees the failure and skips emitting the event
    }
  }
}

object DynamoMatchStateDiffer {
  // FootballMatchContentState contains a sealed `MatchStatus` hierarchy which Scanamo cannot
  // reliably auto-derive, so we reuse the existing Play JSON formats and store it as a JSON string.
  implicit val footballMatchContentStateDynamoFormat: DynamoFormat[FootballMatchContentState] = {
    // todo copilot
    // If parsing/decoding throws (bad or malformed JSON), coercedXmap catches that Throwable and surfaces it as a DynamoReadError.
    DynamoFormat.coercedXmap[FootballMatchContentState, String, Throwable](
      json => Json.parse(json).as[FootballMatchContentState]
    )(
      state => Json.toJson(state).toString
    )
  }
}

