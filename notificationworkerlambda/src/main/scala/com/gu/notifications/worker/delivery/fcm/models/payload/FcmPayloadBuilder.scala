package com.gu.notifications.worker.delivery.fcm.models.payload

import java.net.URI
import java.util.UUID

import scala.PartialFunction._
import collection.JavaConverters._
import com.google.firebase.messaging.AndroidConfig
import com.gu.notifications.worker.delivery.FcmPayload
import com.gu.notifications.worker.delivery.fcm.models.payload.Editions.Edition
import com.gu.notifications.worker.delivery.utils.TimeToLive._
import models._

object FcmPayloadBuilder {

  def apply(notification: Notification, debug: Boolean): Option[FcmPayload] =
    FirebaseAndroidNotification(notification, debug).map(n => new FcmPayload(n.androidConfig))

   private[payload] case class FirebaseAndroidNotification(notificationId: UUID, data: Map[String, String], ttl: Long = DefaulTtl) {
    def androidConfig: AndroidConfig =
      AndroidConfig.builder()
        .putAllData(
          data
            .updated(Keys.UniqueIdentifier, notificationId.toString)
            .updated(Keys.Provider, Provider.Guardian.value)
            .asJava
        )
        .setPriority(AndroidConfig.Priority.HIGH)
        .setTtl(ttl)
        .build()
  }

  private[payload] object FirebaseAndroidNotification {
    def apply(notification: Notification, debug: Boolean): Option[FirebaseAndroidNotification] = notification match {
      case n: BreakingNewsNotification => Some(breakingNewsAndroidNotification(n, debug))
      case n: ContentNotification => Some(contentAndroidNotification(n, debug))
      case n: FootballMatchStatusNotification => Some(footballMatchStatusAndroidNotification(n))
      case n: EditionsShardNotification => Some(editionsAndroidNotification(n))
      case _ => None
    }
  }

  private def breakingNewsAndroidNotification(breakingNews: BreakingNewsNotification, debug: Boolean): FirebaseAndroidNotification = {

    val sectionLink = condOpt(breakingNews.link) {
      case Link.Internal(contentApiId, _, GITSection) => contentApiId
    }

    val tagLink = condOpt(breakingNews.link) {
      case Link.Internal(contentApiId, _, GITTag) => contentApiId
    }

    val editions = breakingNews.topic
      .filter(_.`type` == TopicTypes.Breaking)
      .map(_.name)
      .collect(Edition.fromString)

    val androidLink = toAndroidLink(breakingNews.link)
    val platformLink = toPlatformLink(breakingNews.link)
    val edition = if (editions.size == 1) Some(editions.head) else None
    val keyword = tagLink.map(new URI(_))

    FirebaseAndroidNotification(
      notificationId = breakingNews.id,
      data = Map(
        Keys.NotificationType -> breakingNews.`type`.value,
        Keys.Type -> MessageTypes.Custom,
        Keys.Title -> breakingNews.title,
        Keys.Ticker -> breakingNews.message,
        Keys.Message -> breakingNews.message,
        Keys.Debug -> debug.toString,
        Keys.Editions -> editions.mkString(","),
        Keys.Link -> toAndroidLink(breakingNews.link).toString,
        Keys.UriType -> platformLink.`type`,
        Keys.Uri -> platformLink.uri
      ) ++ sectionLink.map(new URI(_)).map(Keys.Section -> _.toString).toMap
        ++ edition.map(Keys.Edition -> _.toString).toMap
        ++ keyword.map(Keys.Keyword -> _.toString).toMap
        ++ breakingNews.imageUrl.map(Keys.ImageUrl -> _.toString).toMap
        ++ breakingNews.thumbnailUrl.map(Keys.ThumbnailUrl -> _.toString).toMap,
      ttl = BreakingNewsTtl
    )
  }

  private def contentAndroidNotification(cn: ContentNotification, debug: Boolean): FirebaseAndroidNotification = {
    val link = toPlatformLink(cn.link)

    FirebaseAndroidNotification(
      notificationId = cn.id,
      Map(
        Keys.Type -> MessageTypes.Custom,
        Keys.Title -> cn.title,
        Keys.Ticker -> cn.message,
        Keys.Message -> cn.message,
        Keys.Link -> toAndroidLink(cn.link).toString,
        Keys.Topics -> cn.topic.map(toAndroidTopic).mkString(","),
        Keys.UriType -> link.`type`,
        Keys.Uri -> new URI(link.uri).toString,
        Keys.Debug -> debug.toString
      ) ++ cn.thumbnailUrl.map(Keys.ThumbnailUrl -> _.toString).toMap
    )
  }

  private def footballMatchStatusAndroidNotification(matchStatusAlert: FootballMatchStatusNotification): FirebaseAndroidNotification =
    FirebaseAndroidNotification(
      notificationId = matchStatusAlert.id,
      data = Map(
        Keys.Type -> MessageTypes.FootballMatchAlert,
        Keys.HomeTeamName -> matchStatusAlert.homeTeamName,
        Keys.HomeTeamId -> matchStatusAlert.homeTeamId,
        Keys.HomeTeamScore -> matchStatusAlert.homeTeamScore.toString,
        Keys.HomeTeamText -> matchStatusAlert.homeTeamMessage,
        Keys.AwayTeamName -> matchStatusAlert.awayTeamName,
        Keys.AwayTeamId -> matchStatusAlert.awayTeamId,
        Keys.AwayTeamScore -> matchStatusAlert.awayTeamScore.toString,
        Keys.AwayTeamText -> matchStatusAlert.awayTeamMessage,
        Keys.CurrentMinute -> "",
        Keys.Importance -> matchStatusAlert.importance.toString,
        Keys.MatchStatus -> matchStatusAlert.matchStatus,
        Keys.MatchId -> matchStatusAlert.matchId,
        Keys.MatchInfoUri -> matchStatusAlert.matchInfoUri.toString
      ) ++ matchStatusAlert.articleUri.map(Keys.ArticleUri -> _.toString).toMap
        ++ matchStatusAlert.competitionName.map(Keys.CompetitionName -> _).toMap
        ++ matchStatusAlert.venue.map(Keys.Venue -> _).toMap,
      ttl = FootballMatchStatusTtl
    )

  private def editionsAndroidNotification(editionsShardNotification: EditionsShardNotification): FirebaseAndroidNotification =
    FirebaseAndroidNotification(
      notificationId = editionsShardNotification.id,
      data = Map (
        Keys.Type -> MessageTypes.Custom,
        Keys.Topics -> editionsShardNotification.topic.map(toAndroidTopic).mkString(","),
        Keys.Message -> editionsShardNotification.message,
        Keys.Importance -> editionsShardNotification.importance.toString
      )
    )

  private case class PlatformUri(uri: String, `type`: String)

  private def toPlatformLink(link: Link): PlatformUri = link match {
    case Link.Internal(contentApiId, _, _) => PlatformUri(s"x-gu:///items/$contentApiId", "item")
    case Link.External(url) => PlatformUri(url, "external")
  }

  private def toAndroidLink(link: Link) = link match {
    case Link.Internal(contentApiId, _, _) => new URI(s"x-gu://www.guardian.co.uk/$contentApiId")
    case Link.External(url) => new URI(url)
  }

  private def toAndroidTopic(topic: Topic) = s"${topic.`type`}//${topic.name}"
}


