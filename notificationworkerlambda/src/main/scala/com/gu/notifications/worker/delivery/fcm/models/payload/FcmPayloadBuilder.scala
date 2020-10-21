package com.gu.notifications.worker.delivery.fcm.models.payload

import java.net.URI
import java.util.UUID

import com.google.firebase.messaging.AndroidConfig
import com.gu.notifications.worker.delivery.FcmPayload
import com.gu.notifications.worker.delivery.fcm.models.payload.Editions.Edition
import com.gu.notifications.worker.delivery.utils.TimeToLive._
import models._

import scala.PartialFunction._
import scala.jdk.CollectionConverters._

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
      case n: EditionsNotification => Some(editionsAndroidNotification(n))
      case n: Us2020ResultsNotification => Some(us2020ResultsNotification(n))
      case _ => None
    }
  }

  private def breakingNewsAndroidNotification(breakingNews: BreakingNewsNotification, debug: Boolean): FirebaseAndroidNotification = {

    val sectionLink = condOpt(breakingNews.link) {
      case Link.Internal(contentApiId, _, GITSection, _) => contentApiId
    }

    val tagLink = condOpt(breakingNews.link) {
      case Link.Internal(contentApiId, _, GITTag, _) => contentApiId
    }

    val editions = breakingNews.topic
      .filter(_.`type` == TopicTypes.Breaking)
      .map(_.name)
      .collect(Edition.fromString)

    val platformLink = toPlatformLink(breakingNews.link)
    val edition = if (editions.size == 1) Some(editions.head) else None
    val keyword = tagLink.map(new URI(_))
    val title = breakingNews.title.getOrElse("The Guardian")

    FirebaseAndroidNotification(
      notificationId = breakingNews.id,
      data = Map(
        Keys.NotificationType -> breakingNews.`type`.value,
        Keys.Type -> MessageTypes.Custom,
        Keys.Debug -> debug.toString,
        Keys.Editions -> editions.mkString(","),
        Keys.Link -> toAndroidLink(breakingNews.link).toString,
        Keys.UriType -> platformLink.`type`,
        Keys.Uri -> platformLink.uri,
        Keys.Title -> title,
      ) ++ sectionLink.map(new URI(_)).map(Keys.Section -> _.toString).toMap
        ++ edition.map(Keys.Edition -> _.toString).toMap
        ++ keyword.map(Keys.Keyword -> _.toString).toMap
        ++ breakingNews.imageUrl.map(Keys.ImageUrl -> _.toString).toMap
        ++ breakingNews.thumbnailUrl.map(Keys.ThumbnailUrl -> _.toString).toMap
        ++ breakingNews.message.map(Keys.Message -> _).toMap
        ++ breakingNews.message.map(Keys.Ticker -> _).toMap,
      ttl = BreakingNewsTtl
    )
  }

  private def contentAndroidNotification(cn: ContentNotification, debug: Boolean): FirebaseAndroidNotification = {
    val link = toPlatformLink(cn.link)

    FirebaseAndroidNotification(
      notificationId = cn.id,
      Map(
        Keys.Type -> MessageTypes.Custom,
        Keys.Link -> toAndroidLink(cn.link).toString,
        Keys.Topics -> cn.topic.map(toAndroidTopic).mkString(","),
        Keys.UriType -> link.`type`,
        Keys.Uri -> new URI(link.uri).toString,
        Keys.Debug -> debug.toString,
        Keys.Title -> cn.title.getOrElse("")
      ) ++ cn.thumbnailUrl.map(Keys.ThumbnailUrl -> _.toString).toMap
        ++ cn.message.map(Keys.Message -> _.toString).toMap
        ++ cn.message.map(Keys.Ticker -> _.toString).toMap
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

  private def editionsAndroidNotification(editionsShardNotification: EditionsNotification): FirebaseAndroidNotification =
    FirebaseAndroidNotification(
      notificationId = editionsShardNotification.id,
      data = Map (
        Keys.Type -> MessageTypes.Custom,
        Keys.NotificationType -> editionsShardNotification.`type`.value,
        Keys.Topics -> editionsShardNotification.topic.map(toAndroidTopic).mkString(","),
        Keys.Importance -> editionsShardNotification.importance.toString,
        Keys.EditionsDate -> editionsShardNotification.date,
        Keys.EditionsKey -> editionsShardNotification.key,
        Keys.EditionsName -> editionsShardNotification.name
      ) ++ editionsShardNotification.message.map(Keys.Message -> _.toString).toMap
    )

  private def us2020ResultsNotification(notification: Us2020ResultsNotification): FirebaseAndroidNotification =
    FirebaseAndroidNotification(
      notificationId = notification.id,
      data = Map(
        Keys.Type -> MessageTypes.Us2020Results,
        Keys.Importance -> notification.importance.toString,
        Keys.Topics -> notification.topic.map(toAndroidTopic).mkString(","),
        Keys.Title -> notification.title.toString,
        Keys.ExpandedTitle -> notification.expandedTitle,
        Keys.LeftCandidateName -> notification.leftCandidateName,
        Keys.LeftCandidateColour -> notification.leftCandidateColour,
        Keys.LeftCandidateDelegates -> notification.leftCandidateDelegates.toString,
        Keys.LeftCandidateVoteShare -> notification.leftCandidateVoteShare.toString,
        Keys.RightCandidateName -> notification.rightCandidateName,
        Keys.RightCandidateColour -> notification.rightCandidateColour,
        Keys.RightCandidateDelegates -> notification.rightCandidateDelegates.toString,
        Keys.RightCandidateVoteShare -> notification.rightCandidateVoteShare.toString,
        Keys.TotalDelegates -> notification.totalDelegates.toString,
        Keys.Message -> notification.message.toString,
        Keys.ExpandedMessage-> notification.expandedMessage,
        Keys.Button1Text -> notification.button1Text,
        Keys.Button1Url -> notification.button1Url,
        Keys.Button2Text -> notification.button2Text,
        Keys.Button2Url -> notification.button2Url
      ),
      ttl = BreakingNewsTtl
    )

  private case class PlatformUri(uri: String, `type`: String)

  private def toPlatformLink(link: Link): PlatformUri = link match {
    case Link.Internal(contentApiId, _, _, Some(blockId)) => PlatformUri(s"x-gu:///items/$contentApiId?page=with:block-$blockId#block-$blockId", "item")
    case Link.Internal(contentApiId, _, _, None) => PlatformUri(s"x-gu:///items/$contentApiId", "item")
    case Link.External(url) => PlatformUri(url, "external")
  }

  private def toAndroidLink(link: Link) = link match {
    case Link.Internal(contentApiId, _, _, Some(blockId)) => new URI(s"x-gu://www.guardian.co.uk/$contentApiId?page=with:block-$blockId#block-$blockId")
    case Link.Internal(contentApiId, _, _, None) => new URI(s"x-gu://www.guardian.co.uk/$contentApiId")
    case Link.External(url) => new URI(url)
  }

  private def toAndroidTopic(topic: Topic) = s"${topic.`type`}//${topic.name}"
}


