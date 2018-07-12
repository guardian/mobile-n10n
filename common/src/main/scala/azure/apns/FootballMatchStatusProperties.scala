package azure.apns

import play.api.libs.json.Json
import utils.MapImplicits._

case class FootballMatchStatusProperties(
  homeTeamName: String,
  homeTeamId: String,
  homeTeamScore: Int,
  homeTeamText: String,
  awayTeamName: String,
  awayTeamId: String,
  awayTeamScore: Int,
  awayTeamText: String,
  currentMinute: String,
  matchStatus: String,
  matchId: String,
  mapiUrl: String,
  matchInfoUri: String,
  articleUri: Option[String],
  uri: String,
  competitionName: Option[String],
  venue: Option[String]
) {
  def toMap: Map[String, Any] = Map(
    "homeTeamName" -> homeTeamName,
    "homeTeamId" -> homeTeamId,
    "homeTeamScore" -> homeTeamScore,
    "homeTeamText" -> homeTeamText,
    "awayTeamName" -> awayTeamName,
    "awayTeamId" -> awayTeamId,
    "awayTeamScore" -> awayTeamScore,
    "awayTeamText" -> awayTeamText,
    "currentMinute" -> currentMinute,
    "matchStatus" -> matchStatus,
    "matchId" -> matchId,
    "mapiUrl" -> mapiUrl,
    "matchInfoUri" -> matchInfoUri,
    "uri" -> uri
  ) ++ Map(
    "articleUri" -> articleUri,
    "competitionName" -> competitionName,
    "venue" -> venue
  ).flattenValues
}
object FootballMatchStatusProperties {
  implicit val jf = Json.format[FootballMatchStatusProperties]
}