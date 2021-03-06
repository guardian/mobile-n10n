package com.gu.notifications.worker.delivery.apns.models.payload

import java.net.URI
import java.util.UUID

import _root_.models.NotificationType._
import _root_.models._
import com.gu.notifications.worker.delivery.ApnsPayload
import com.gu.notifications.worker.delivery.apns.models.ApnsConfig
import com.gu.notifications.worker.delivery.utils.TimeToLive._
import com.gu.notifications.worker.delivery.apns.models.payload.CustomProperty.Keys
import com.gu.notifications.worker.delivery.apns.models.payload.PlatformUriTypes.{External, Item}
import com.turo.pushy.apns.util.{ApnsPayloadBuilder => Builder}
import com.turo.pushy.apns.{DeliveryPriority, PushType}

class ApnsPayloadBuilder(config: ApnsConfig) {

  def apply(notification: Notification): Option[ApnsPayload] = notification match {
      case n: BreakingNewsNotification => Some(breakingNewsPayload(n))
      case n: ContentNotification => Some(contentPayload(n))
      case n: FootballMatchStatusNotification => Some(footballMatchStatusPayload(n))
      case n: NewsstandShardNotification => Some(newsstandPayload(n))
      case n: EditionsNotification => Some(editionsPayload(n))
      case n: Us2020ResultsNotification => Some(us2020ResultsPayload(n))
      case _ => None
  }

  private case class PushyPayload(
    alertTitle: Option[String] = None,
    alertBody: Option[String] = None,
    categoryName: Option[String] = None,
    contentAvailable: Boolean = false,
    mutableContent: Boolean = false,
    sound: Option[String] = None,
    customProperties: Seq[CustomProperty] = Seq()
  ) {
    def payload: String = {
      val payloadBuilder = new Builder()
      alertTitle.foreach(payloadBuilder.setAlertTitle)
      alertBody.foreach(payloadBuilder.setAlertBody)
      categoryName.foreach(payloadBuilder.setCategoryName)
      payloadBuilder.setContentAvailable(contentAvailable)
      payloadBuilder.setMutableContent(mutableContent)
      sound.foreach(payloadBuilder.setSound)
      customProperties.foreach { p =>
        val (key, value) = p match {
          case CustomPropertyString(k, v) => (k, v)
          case CustomPropertyInt(k, v) => (k, v)
          case CustomPropertySeq(k, v) =>
            import scala.jdk.CollectionConverters._
            (k, v.map(p => (p.key, p.value)).toMap.asJava) // .asJava because Gson which is used by Pushy to encode custom properties doesn't seem to like Scala collections
        }
        payloadBuilder.addCustomProperty(key, value)
      }
      payloadBuilder.buildWithDefaultMaximumLength()
    }
  }

  private def breakingNewsPayload(n: BreakingNewsNotification): ApnsPayload = {
    val link = toPlatformLink(n.link)
    val imageUrl = n.thumbnailUrl.orElse(n.imageUrl)
    val payload = PushyPayload(
      alertTitle = n.title,
      alertBody = n.message,
      categoryName = Option(n.link match {
        case _: Link.External => ""
        case _: Link.Internal => "ITEM_CATEGORY"
      }),
      mutableContent = true,
      sound = Some("default"),
      customProperties = Seq(
        CustomProperty(Keys.UniqueIdentifier -> n.id.toString),
        CustomProperty(Keys.Provider -> Provider.Guardian.value),
        CustomProperty(Keys.MessageType -> MessageTypes.NewsAlert),
        CustomProperty(Keys.NotificationType -> BreakingNews.value),
        CustomProperty(Keys.Link -> toIosLink(n.link).toString),
        CustomProperty(Keys.Topics -> n.topic.map(_.toString).mkString(",")),
        CustomProperty(Keys.Uri -> new URI(link.uri).toString),
        CustomProperty(Keys.UriType -> link.`type`.toString)
      ) ++ imageUrl.map(u => CustomProperty(Keys.ImageUrl -> u.toString)).toSeq
    ).payload
    ApnsPayload(payload, Some(BreakingNewsTtl), toCollapseId(n.link), PushType.ALERT)
  }

  private def contentPayload(n: ContentNotification): ApnsPayload = {
    val link = toPlatformLink(n.link)
    val payLoad = PushyPayload(
      alertTitle = n.title,
      alertBody = n.message,
      categoryName = Some("ITEM_CATEGORY"),
      mutableContent = true,
      sound = Some("default"),
      customProperties = Seq(
        CustomProperty(Keys.UniqueIdentifier -> n.id.toString),
        CustomProperty(Keys.Provider -> Provider.Guardian.value),
        CustomProperty(Keys.MessageType -> MessageTypes.NewsAlert),
        CustomProperty(Keys.NotificationType -> Content.value),
        CustomProperty(Keys.Link -> toIosLink(n.link).toString),
        CustomProperty(Keys.Topics -> n.topic.map(_.toString).mkString(",")),
        CustomProperty(Keys.Uri -> new URI(link.uri).toString),
        CustomProperty(Keys.UriType -> link.`type`.toString)
      )
    ).payload
    ApnsPayload(payLoad, None, toCollapseId(n.link), PushType.ALERT)
  }

  private def footballMatchStatusPayload(n: FootballMatchStatusNotification): ApnsPayload = {
    val payLoad = PushyPayload(
      alertTitle = n.title,
      alertBody = n.message,
      categoryName = Some("football-match"),
      mutableContent = true,
      sound = if (n.importance == Importance.Major) Some("default") else None,
      customProperties = Seq(
        CustomProperty(Keys.UniqueIdentifier -> n.id.toString),
        CustomProperty(Keys.Provider -> Provider.Guardian.value),
        CustomProperty(Keys.MessageType -> MessageTypes.FootballMatchStatus),
        CustomProperty(Keys.NotificationType -> FootballMatchStatus.value),
        CustomProperty(Keys.FootballMatch -> (
          Seq(
            CustomProperty(Keys.HomeTeamName -> n.homeTeamName),
            CustomProperty(Keys.HomeTeamId -> n.homeTeamId),
            CustomProperty(Keys.HomeTeamScore -> n.homeTeamScore),
            CustomProperty(Keys.HomeTeamText -> n.homeTeamMessage),
            CustomProperty(Keys.AwayTeamName -> n.awayTeamName),
            CustomProperty(Keys.AwayTeamId -> n.awayTeamId),
            CustomProperty(Keys.AwayTeamScore -> n.awayTeamScore),
            CustomProperty(Keys.AwayTeamText -> n.awayTeamMessage),
            CustomProperty(Keys.CurrentMinute -> ""),
            CustomProperty(Keys.MatchStatus -> n.matchStatus),
            CustomProperty(Keys.MatchId -> n.matchId),
            CustomProperty(Keys.MapiUrl -> n.matchInfoUri.toString),
            CustomProperty(Keys.MatchInfoUri -> n.matchInfoUri.toString),
            CustomProperty(Keys.Uri -> "")
          ) ++
            n.articleUri.map(u => CustomProperty(Keys.ArticleUri -> u.toString)).toSeq ++
            n.competitionName.map(c => CustomProperty(Keys.CompetitionName -> c)).toSeq ++
            n.venue.map(v => CustomProperty(Keys.Venue -> v)).toSeq
          )
        )
      )
    ).payload
    ApnsPayload(payLoad, Some(FootballMatchStatusTtl), Some(n.matchId), PushType.ALERT)
  }

  private def newsstandPayload(notification: NewsstandShardNotification): ApnsPayload =
    ApnsPayload(
      jsonString = PushyPayload(contentAvailable = true).payload,
      ttl = None,
      collapseId = None,
      pushType = PushType.BACKGROUND,
      deliveryPriority = DeliveryPriority.CONSERVE_POWER
    )

  private def editionsPayload(notification: EditionsNotification): ApnsPayload =
    ApnsPayload(
      jsonString = PushyPayload(
        customProperties = Seq(
          CustomProperty(Keys.UniqueIdentifier -> notification.id.toString),
          CustomProperty(Keys.EditionsDate -> notification.date),
          CustomProperty(Keys.EditionsKey -> notification.key),
          CustomProperty(Keys.EditionsName -> notification.name)
        ),
        contentAvailable = true
      ).payload,
      ttl = None,
      collapseId = None,
      pushType = PushType.BACKGROUND,
      deliveryPriority = DeliveryPriority.CONSERVE_POWER
    )

  private def us2020ResultsPayload(n: Us2020ResultsNotification): ApnsPayload = {
    val link = toPlatformLink(n.link)
    val payLoad = PushyPayload(
      alertTitle = n.title,
      alertBody = n.message,
      categoryName = Some("us-election-2020"),
      mutableContent = true,
      sound = if (n.importance == Importance.Major) Some("default") else None,
      customProperties = Seq(
        CustomProperty(Keys.UniqueIdentifier -> n.id.toString),
        CustomProperty(Keys.Provider -> Provider.Guardian.value),
        CustomProperty(Keys.MessageType -> MessageTypes.UsElection2020),
        CustomProperty(Keys.Link -> toIosLink(n.link).toString),
        CustomProperty(Keys.Uri -> new URI(link.uri).toString),
        CustomProperty(Keys.NotificationType -> Content.value),
        CustomProperty(Keys.UsElection2020 ->
          Seq(
            CustomProperty(Keys.ExpandedTitle -> n.expandedTitle),
            CustomProperty(Keys.LeftCandidateName -> n.leftCandidateName),
            CustomProperty(Keys.LeftCandidateColour -> n.leftCandidateColour),
            CustomProperty(Keys.LeftCandidateColourDark -> n.leftCandidateColourDark),
            CustomProperty(Keys.LeftCandidateDelegates -> n.leftCandidateDelegates),
            CustomProperty(Keys.LeftCandidateVoteShare -> n.leftCandidateVoteShare),
            CustomProperty(Keys.RightCandidateName -> n.rightCandidateName),
            CustomProperty(Keys.RightCandidateColour -> n.rightCandidateColour),
            CustomProperty(Keys.RightCandidateColourDark -> n.rightCandidateColourDark),
            CustomProperty(Keys.RightCandidateDelegates -> n.rightCandidateDelegates),
            CustomProperty(Keys.DelegatesToWin -> n.delegatesToWin),
            CustomProperty(Keys.RightCandidateVoteShare -> n.rightCandidateVoteShare),
            CustomProperty(Keys.TotalDelegates -> n.totalDelegates),
            CustomProperty(Keys.ExpandedMessage -> n.expandedMessage),
            CustomProperty(Keys.Button1Text -> n.button1Text),
            CustomProperty(Keys.Button1Url -> n.button1Url),
            CustomProperty(Keys.Button2Text -> n.button2Text),
            CustomProperty(Keys.Button2Url -> n.button2Url),
            CustomProperty(Keys.StopButtonText -> n.stopButtonText)
          )
        )
      )
    ).payload
    ApnsPayload(payLoad, Some(BreakingNewsTtl), Some("us-election-2020-collapse"), PushType.ALERT)
  }

  private def toPlatformLink(link: Link) = link match {
    case Link.Internal(contentApiId, _, _, Some(blockId)) => PlatformUri(s"https://www.theguardian.com/$contentApiId?page=with:block-$blockId#block-$blockId", Item)
    case Link.Internal(contentApiId, _, _, None) => PlatformUri(s"https://www.theguardian.com/$contentApiId", Item)
    case Link.External(url) => PlatformUri(url, External)
  }

  private def toIosLink(link: Link) = link match {
    case Link.Internal(contentApiId, _, _, Some(blockId)) => new URI(s"${config.mapiBaseUrl}/items/$contentApiId?page=with:block-$blockId#block-$blockId")
    case Link.Internal(contentApiId, _, _, None) => new URI(s"${config.mapiBaseUrl}/items/$contentApiId")
    case _ => link.webUri("http://www.theguardian.com/")
  }

  private def toCollapseId(link: Link): Option[String] = link match {
    case Link.Internal(contentApiId, _, _, _) => Some(UUID.nameUUIDFromBytes(contentApiId.getBytes).toString)
    case _ => None
  }
}


