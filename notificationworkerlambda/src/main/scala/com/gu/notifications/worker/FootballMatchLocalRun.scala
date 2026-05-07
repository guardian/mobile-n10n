package com.gu.notifications.worker

import com.gu.mobile.notifications.client.models.{FootballMatchStatusPayload, NotificationPayload}
import com.gu.mobile.notifications.football.lib.{EventConsumer, SyntheticMatchEventGenerator}
import com.gu.mobile.notifications.football.models.MatchDataWithArticle
import com.gu.mobile.notifications.football.notificationbuilders.MatchStatusNotificationBuilder
import com.google.firebase.messaging.AndroidConfig
import com.gu.notifications.worker.delivery.fcm.models.payload.FcmPayloadBuilder
import pa.{MatchDay, Parser}
import play.api.libs.json._

import java.time.ZonedDateTime
import scala.io.Source
import scala.jdk.CollectionConverters._

/**
 * Run from the root:
  sbt "project notificationworkerlambda" "runMain com.gu.notifications.worker.FootballMatchLocalRun"
 */
object FootballMatchLocalRun extends App {

  // One-time setup to extract the FCM key-value data from AndroidConfig locally.
  private val androidConfigDataField = {
    val f = classOf[AndroidConfig].getDeclaredField("data")
    f.setAccessible(true)
    f
  }

  private val paMatchDayXml    = "football/src/test/resources/worldcup/match-day.xml"
  private val paMatchEventsXml = "football/src/test/resources/worldcup/match-event-feed.xml"

  val eventConsumer = new EventConsumer(new MatchStatusNotificationBuilder("https://mobile.guardianapis.com"))

  val matchDay    = Parser.parseMatchDay(Source.fromFile(paMatchDayXml).mkString).head
  val rawEvents   = Parser.parseMatchEvents(Source.fromFile(paMatchEventsXml).mkString).get.events.toList
  val events      = new SyntheticMatchEventGenerator(() => ZonedDateTime.now()).generate(rawEvents, matchDay.id, matchDay)
  val result      = matchJson(matchDay, rawEvents.size, events.size - rawEvents.size,
    MatchDataWithArticle(matchDay, events, articleId = None))

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

  private def fcmData(fp: FootballMatchStatusPayload): JsObject = {
    val notification = Json.toJson(fp).as[_root_.models.FootballMatchStatusNotification]
    FcmPayloadBuilder(notification, debug = false).map { fcmPayload =>
      val data = androidConfigDataField.get(fcmPayload.androidConfig).asInstanceOf[java.util.Map[String, String]].asScala
      JsObject(data.view.mapValues(JsString(_)).toMap)
    }.getOrElse(JsObject.empty)
  }
}
