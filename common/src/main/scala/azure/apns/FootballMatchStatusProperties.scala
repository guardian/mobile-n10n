package azure.apns

import play.api.libs.json.Json

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
)
object FootballMatchStatusProperties {
  implicit val jf = Json.format[FootballMatchStatusProperties]
}