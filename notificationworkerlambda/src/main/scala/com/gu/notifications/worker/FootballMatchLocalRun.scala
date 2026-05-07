package com.gu.notifications.worker

import com.gu.mobile.notifications.client.models.{FootballMatchStatusPayload, NotificationPayload}
import com.gu.mobile.notifications.football.lib.{EventConsumer, SyntheticMatchEventGenerator}
import com.gu.mobile.notifications.football.models.MatchDataWithArticle
import com.gu.mobile.notifications.football.notificationbuilders.MatchStatusNotificationBuilder
import com.gu.notifications.worker.delivery.fcm.models.payload.{Keys, MessageTypes}
import pa.{MatchDay, Parser}
import play.api.libs.json._

import java.time.ZonedDateTime
import scala.io.Source

/**
 * Run from sbt (project notificationworkerlambda):
 *   run FootballMatchLocalRun <matchday-xml> <match-events-xml>
 *
 * Example:
 *   run FootballMatchLocalRun football/src/test/resources/worldcup/match-day.xml \
 *                             football/src/test/resources/worldcup/match-event-feed.xml
 *
 * Output: pretty-printed JSON object.
 */
object FootballMatchLocalRun extends App {

  if (args.length != 2) {
    System.err.println("Usage: FootballMatchLocalRun <matchday-xml> <match-events-xml>")
    sys.exit(1)
  }

  val eventConsumer = new EventConsumer(new MatchStatusNotificationBuilder("https://mobile.guardianapis.com"))

  val matchDayXml    = Source.fromFile(args(0)).mkString
  val matchEventsXml = Source.fromFile(args(1)).mkString

  val result: JsValue = (Parser.parseMatchDay(matchDayXml).headOption, Parser.parseMatchEvents(matchEventsXml)) match {
    case (None, _) => System.err.println("No match days in XML"); sys.exit(1)
    case (_, None) => System.err.println("No match events in XML"); sys.exit(1)
    case (Some(matchDay), Some(parsed)) =>
      val rawEvents = parsed.events.toList
      val events    = new SyntheticMatchEventGenerator(ZonedDateTime.now()).generate(rawEvents, matchDay.id, matchDay)
      matchJson(matchDay, rawEvents.size, events.size - rawEvents.size,
        MatchDataWithArticle(matchDay, events, articleId = None))
  }

  println(Json.prettyPrint(result))

  private def matchJson(matchDay: MatchDay, rawCount: Int, syntheticCount: Int,
                        matchData: MatchDataWithArticle): JsObject =
    Json.obj(
      "match" -> Json.obj(
        "id"             -> matchDay.id,
        "homeTeam"       -> matchDay.homeTeam.name,
        "awayTeam"       -> matchDay.awayTeam.name,
        "status"         -> matchDay.matchStatus,
        "round"          -> matchDay.round.name,
        "competition"    -> matchDay.competition.map(_.name),
        "rawEvents"      -> rawCount,
        "syntheticEvents" -> syntheticCount
      ),
      "notifications" -> JsArray(eventConsumer.eventsToNotifications(matchData).map(notificationJson))
    )

  private def notificationJson(payload: NotificationPayload): JsObject = {
    val fp = payload.asInstanceOf[FootballMatchStatusPayload]
    Json.obj(
      "title"      -> fp.title,
      "importance" -> fp.importance.toString,
      "message"    -> fp.message,
      "fcmData"    -> fcmData(fp)
    )
  }

  private def fcmData(fp: FootballMatchStatusPayload): JsObject =
    JsObject(
      Map(
        Keys.Type          -> MessageTypes.FootballMatchAlert,
        Keys.HomeTeamName  -> fp.homeTeamName,
        Keys.HomeTeamId    -> fp.homeTeamId,
        Keys.HomeTeamScore -> fp.homeTeamScore.toString,
        Keys.HomeTeamText  -> fp.homeTeamMessage,
        Keys.HomeTeamRedCards -> fp.homeTeamRedCards.toString,
        Keys.AwayTeamName  -> fp.awayTeamName,
        Keys.AwayTeamId    -> fp.awayTeamId,
        Keys.AwayTeamScore -> fp.awayTeamScore.toString,
        Keys.AwayTeamText  -> fp.awayTeamMessage,
        Keys.AwayTeamRedCards -> fp.awayTeamRedCards.toString,
        Keys.CurrentMinute -> "",
        Keys.Importance    -> fp.importance.toString,
        Keys.MatchStatus   -> fp.matchStatus,
        Keys.MatchId       -> fp.matchId,
        Keys.MatchInfoUri  -> fp.matchInfoUri.toString
      ).view.mapValues(JsString(_)).toMap
        ++ fp.articleUri.map(Keys.ArticleUri -> JsString(_.toString))
        ++ fp.competitionName.map(Keys.CompetitionName -> JsString(_))
        ++ fp.venue.map(Keys.Venue -> JsString(_))
        ++ fp.roundName.map(Keys.RoundName -> JsString(_))
    )
}
