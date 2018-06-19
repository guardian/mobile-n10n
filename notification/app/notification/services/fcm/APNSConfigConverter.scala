package notification.services.fcm

import java.net.URI

import azure._
import azure.apns.FootballMatchStatusProperties
import com.google.firebase.messaging.{ApnsConfig, Aps, ApsAlert}
import models.NotificationType.{BreakingNews, Content, FootballMatchStatus}
import models._
import notification.models.ios.Keys
import notification.models.Push
import notification.services.Configuration
import notification.services.azure.PlatformUriType
import notification.services.azure.PlatformUriTypes.{External, Item}
import play.api.Logger

import collection.JavaConverters._

class APNSConfigConverter(conf: Configuration) extends FCMConfigConverter[ApnsConfig] {

  val logger = Logger(classOf[APNSConfigConverter])

  override def toFCM(push: Push): Option[ApnsConfig] = {
    toFirebaseApnsNotification(push).map(_.toApnsConfig)
  }

  def toFirebaseApnsNotification(push: Push): Option[FirebaseApnsNotification] = {
    logger.debug(s"Converting push to Azure: $push")

    PartialFunction.condOpt(push.notification) {
      case contentNotification: ContentNotification => toContent(contentNotification)
      case breakingNews: BreakingNewsNotification => toBreakingNews(breakingNews)
      case football: FootballMatchStatusNotification => toMatchStatusAlert(football)
    }
  }

  case class FirebaseApsAlert(title: String, body: String)

  case class FirebaseApnsNotification(
    category: Option[String],
    alert: Option[Either[String, FirebaseApsAlert]],
    contentAvailable: Option[Boolean],
    mutableContent: Option[Boolean],
    sound: Option[String],
    customData: List[(String, Option[AnyRef])]
  ) {
    def toApnsConfig: ApnsConfig = {
      val apnsConfigBuilder = ApnsConfig.builder()

      val apsBuilder = Aps.builder()
      category.foreach(apsBuilder.setCategory)
      alert match {
        case Some(Left(alertString)) => apsBuilder.setAlert(alertString)
        case Some(Right(alertObject)) => apsBuilder.setAlert(
          ApsAlert.builder()
            .setBody(alertObject.body)
            .setTitle(alertObject.title)
            .build())
        case _ => ()
      }
      contentAvailable.foreach(apsBuilder.setContentAvailable)
      mutableContent.foreach(apsBuilder.setMutableContent)
      sound.foreach(apsBuilder.setSound)
      val aps = apsBuilder.build()

      val allCustomData = customData
        .collect { case (key, Some(value)) => key -> value }
        .toMap.asJava

      apnsConfigBuilder.setAps(aps)
      apnsConfigBuilder.putAllCustomData(allCustomData)
      apnsConfigBuilder.build()
    }
  }

  private def toContent(cn: ContentNotification): FirebaseApnsNotification = {
    val link = toPlatformLink(cn.link)

    FirebaseApnsNotification(
      category = Some("ITEM_CATEGORY"),
      alert = if (cn.iosUseMessage.contains(true)) Some(Left(cn.message)) else Some(Left(cn.title)),
      contentAvailable = Some(true),
      mutableContent = None,
      sound = Some("default"),
      customData = List(
        Keys.NotificationType -> Some(Content.value),
        Keys.Link -> Some(toIosLink(cn.link).toString),
        Keys.Topics -> Some(cn.topic.map(_.toString).mkString(",")),
        Keys.Uri -> Some(new URI(link.uri).toString),
        Keys.UriType -> Some(link.`type`.toString)
      )
    )
  }

  private def toBreakingNews(breakingNews: BreakingNewsNotification): FirebaseApnsNotification = {
    val link = toPlatformLink(breakingNews.link)

    val category = breakingNews.link match {
      case _: Link.External => Some("")
      case _: Link.Internal => Some("ITEM_CATEGORY")
    }
    val imageUrl = breakingNews.thumbnailUrl orElse breakingNews.imageUrl

    FirebaseApnsNotification(
      category = category,
      alert = Some(Left(breakingNews.message)),
      contentAvailable = Some(true),
      mutableContent = if (imageUrl.isDefined) Some(true) else None,
      sound = Some("default"),
      customData = List(
        Keys.NotificationType -> Some(BreakingNews.value),
        Keys.Link -> Some(toIosLink(breakingNews.link).toString),
        Keys.Topics -> Some(breakingNews.topic.map(_.toString).mkString(",")),
        Keys.Uri -> Some(new URI(link.uri).toString),
        Keys.UriType -> Some(link.`type`.toString),
        Keys.ImageUrl -> imageUrl.map(_.toString)
      )
    )
  }

  private def toMatchStatusAlert(matchStatus: FootballMatchStatusNotification): FirebaseApnsNotification = {
    FirebaseApnsNotification(
      category = Some("football-match"),
      alert = Some(Right(FirebaseApsAlert(matchStatus.title, matchStatus.message))),
      contentAvailable = Some(true),
      mutableContent = Some(true),
      sound = if (matchStatus.importance == Importance.Major) Some("default") else None,
      customData = List(
        Keys.NotificationType -> Some(FootballMatchStatus.value),
        "matchStatus" -> Some(FootballMatchStatusProperties(
          homeTeamName = matchStatus.homeTeamName,
          homeTeamId = matchStatus.homeTeamId,
          homeTeamScore = matchStatus.homeTeamScore,
          homeTeamText = matchStatus.homeTeamMessage,
          awayTeamName = matchStatus.awayTeamName,
          awayTeamId = matchStatus.awayTeamId,
          awayTeamScore = matchStatus.awayTeamScore,
          awayTeamText = matchStatus.awayTeamMessage,
          currentMinute = "",
          matchStatus = matchStatus.matchStatus,
          matchId = matchStatus.matchId,
          mapiUrl = matchStatus.matchInfoUri.toString,
          matchInfoUri = matchStatus.matchInfoUri.toString,
          articleUri = matchStatus.articleUri.map(_.toString),
          uri = "",
          competitionName = matchStatus.competitionName,
          venue = matchStatus.venue
        ))
      )
    )
  }

  case class PlatformUri(uri: String, `type`: PlatformUriType)

  private def toPlatformLink(link: Link) = link match {
    case Link.Internal(contentApiId, _, _) => PlatformUri(s"https://www.theguardian.com/$contentApiId", Item)
    case Link.External(url) => PlatformUri(url, External)
  }

  private def toTags(destination: Set[Topic]) = Some(Tags.fromTopics(destination))

  private def toIosLink(link: Link) = link match {
    case Link.Internal(contentApiId, Some(shortUrl), _) => new URI(s"x-gu://${new URI(shortUrl).getPath}")
    case _ => link.webUri(conf.frontendBaseUrl)
  }

  private def replaceHost(uri: URI) =
    new URI(List(Some("x-gu://"), Option(uri.getPath), Option(uri.getQuery).map("?" + _)).flatten.mkString)

}