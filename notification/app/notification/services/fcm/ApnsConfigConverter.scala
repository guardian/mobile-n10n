package notification.services.fcm

import java.net.URI

import azure._
import azure.apns.{Alert, ElectionProperties, FootballMatchStatusProperties, LiveEventProperties}
import com.google.firebase.messaging.{ApnsConfig, Aps, ApsAlert}
import models.Importance.Major
import models.NotificationType.{BreakingNews, Content, ElectionsAlert, FootballMatchStatus, LiveEventAlert}
import models._
import notification.models.Destination._
import notification.models.ios.{Keys, MessageTypes}
import notification.models.{Push, ios}
import notification.services.Configuration
import notification.services.azure.PlatformUriType
import notification.services.azure.PlatformUriTypes.{External, Item}
import play.api.Logger
import utils.MapImplicits.RichOptionMap

import collection.JavaConverters._

class ApnsConfigConverter(conf: Configuration) {

  val logger = Logger(classOf[ApnsConfigConverter])

  def toIosConfig(push: Push): ApnsConfig = {
    logger.debug(s"Converting push to Azure: $push")

    // Builders are mutable, keep that in mind while reading the following
    val firebaseApnsNotification = push.notification match {
      case contentNotification: ContentNotification => toContent(contentNotification)
      case breakingNews: BreakingNewsNotification => toBreakingNews(breakingNews)
      case election: ElectionNotification => toElectionAlert(election)
      case live: LiveEventNotification => toLiveEventAlert(live)
      case football: FootballMatchStatusNotification => toMatchStatusAlert(football)
    }
    firebaseApnsNotification.toApnsConfig
  }

  private case class FirebaseApsAlert(title: String, body: String)

  private case class FirebaseApnsNotification(
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
        Keys.MessageType -> Some(MessageTypes.NewsAlert),
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
        Keys.MessageType -> Some(MessageTypes.NewsAlert),
        Keys.NotificationType -> Some(BreakingNews.value),
        Keys.Link -> Some(toIosLink(breakingNews.link).toString),
        Keys.Topics -> Some(breakingNews.topic.map(_.toString).mkString(",")),
        Keys.Uri -> Some(new URI(link.uri).toString),
        Keys.UriType -> Some(link.`type`.toString),
        Keys.ImageUrl -> imageUrl
      )
    )
  }

  private def toElectionAlert(electionAlert: ElectionNotification): FirebaseApnsNotification = {
    val democratVotes = electionAlert.results.candidates.find(_.name == "Clinton").map(_.electoralVotes).getOrElse(0)
    val republicanVotes = electionAlert.results.candidates.find(_.name == "Trump").map(_.electoralVotes).getOrElse(0)

    FirebaseApnsNotification(
      category = None,
      alert = None,
      contentAvailable = Some(true),
      mutableContent = None,
      sound = None,
      customData = List(
        Keys.MessageType -> Some(MessageTypes.ElectionAlert),
        Keys.NotificationType -> Some(ElectionsAlert.value),
        "election" -> Some(ElectionProperties(
          title = electionAlert.title,
          body = electionAlert.message,
          richviewbody = electionAlert.expandedMessage.getOrElse(electionAlert.message),
          sound = if (electionAlert.importance == Major) 1 else 0,
          dem = democratVotes,
          rep = republicanVotes,
          link = toIosLink(electionAlert.link).toString,
          results = toIosLink(electionAlert.resultsLink).toString
        ))
      )
    )
  }

  private def toLiveEventAlert(liveEvent: LiveEventNotification): FirebaseApnsNotification = {
    FirebaseApnsNotification(
      category = None,
      alert = None,
      contentAvailable = Some(true),
      mutableContent = None,
      sound = None,
      customData = List(
        Keys.MessageType -> Some(MessageTypes.LiveEventAlert),
        Keys.NotificationType -> Some(LiveEventAlert.value),
        "liveEvent" -> Some(LiveEventProperties(
          title = liveEvent.title,
          body = liveEvent.message,
          richviewbody = liveEvent.expandedMessage.getOrElse(liveEvent.message),
          sound = if (liveEvent.importance == Major) 1 else 0,
          link1 = toIosLink(liveEvent.link1).toString,
          link2 = toIosLink(liveEvent.link2).toString,
          imageURL = liveEvent.imageUrl.map(_.toString),
          topics = liveEvent.topic.toList.map(_.toString).mkString(",")
        ))
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
        Keys.MessageType -> Some(MessageTypes.FootballMatchStatus),
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
          matchStatus = matchStatus.phase,
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

  private def toTags(destination: Destination) = destination match {
    case Left(topics: Set[Topic]) => Some(Tags.fromTopics(topics))
    case Right(user: UniqueDeviceIdentifier) => Some(Tags.fromUserId(user))
  }

  private def toIosLink(link: Link) = link match {
    case Link.Internal(contentApiId, Some(shortUrl), _) => new URI(s"x-gu://${new URI(shortUrl).getPath}")
    case _ => link.webUri(conf.frontendBaseUrl)
  }

  private def replaceHost(uri: URI) =
    new URI(List(Some("x-gu://"), Option(uri.getPath), Option(uri.getQuery).map("?" + _)).flatten.mkString)

}