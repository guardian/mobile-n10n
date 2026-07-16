package com.gu.mobile.notifications.football.lib

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.gu.mobile.notifications.client.models.liveActitivites.{FootballMatchContentState, LiveActivityPayload}
import com.gu.mobile.notifications.football.Logging
import org.scanamo.{ScanamoAsync, Table}
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

// This is used to pre-diff the state in FootballData fetcher to determine if state-change synthetic event should be generated.
// It is not used to determine if a notification should be sent, that is handled by DynamoDistinctCheck.
// Both classes read the same table data.
class DynamoPayloadStateCheck(client: AmazonDynamoDBAsync, tableName: String) extends Logging {

  def isMatchStateIdentical(id: String, state: FootballMatchContentState)(implicit ec: ExecutionContext): Future[Boolean] = {
    import org.scanamo.generic.auto._
    import org.scanamo.syntax._

    val scanamoAsync: ScanamoAsync = ScanamoAsync(client)
    val payloadTable = Table[DynamoMatchLiveActivity](tableName)
    val lastPayloadIndex = payloadTable.index("lastPayload-index") // only live activities payload table has an index.

    val gsiQuery = lastPayloadIndex.descending.limit(1).query("liveActivityID" -> id)

    scanamoAsync.exec(gsiQuery).map { rows =>
      isIdenticalToLatest(rows.collect { case Right(row) => row }, state)
    } recover {
      case e =>
        logger.error(s"Failure while checking for dynamodb GSI for match state $tableName: ${e.getMessage}.")
        false // todo should this be true - do we want to generate a synthetic state-change event if we can't check the latest state?
    }
  }

  private[lib] def isIdenticalToLatest(rows: List[DynamoMatchLiveActivity], state: FootballMatchContentState): Boolean =
    rows.headOption
      .flatMap(row => footballStateFromPayload(row.payload))
      .forall(latest => ignoringArticleUrl(latest, state))

  // articleUrl is only added to the payload after isMatchStateIdentical is called, so we must ignore it.
  private def ignoringArticleUrl(a: FootballMatchContentState, b: FootballMatchContentState): Boolean =
    a.copy(articleUrl = None) == b.copy(articleUrl = None) // ensure articleURL is None for both at the point of comparison

  // The stored `payload` column is the JSON-serialised LiveActivityPayload; the match state we diff
  // against is its `broadcastContentStateData` subfield.
  private[lib] def footballStateFromPayload(payloadJson: String): Option[FootballMatchContentState] =
    Json.parse(payloadJson).as[LiveActivityPayload].broadcastContentStateData.collect {
      case footballState: FootballMatchContentState => footballState
    }
}