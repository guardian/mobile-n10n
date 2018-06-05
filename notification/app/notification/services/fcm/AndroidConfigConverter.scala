package notification.services.fcm

import java.net.URI

import _root_.azure.Tags
import com.google.firebase.messaging.AndroidConfig
import models._
import notification.models.Destination._
import notification.models.android.Editions.Edition
import notification.models.android.{AndroidMessageTypes, Keys}
import notification.models.{Push, android}
import notification.services.Configuration
import play.api.Logger
import utils.MapImplicits._

import scala.PartialFunction._
import collection.JavaConverters._

class AndroidConfigConverter(conf: Configuration) {

  val logger = Logger(classOf[AndroidConfigConverter])

  def toAndroidConfig(push: Push): Option[AndroidConfig] = {
    logger.debug(s"Converting push to android FCM: $push")
    val firebaseAndroidNotification = PartialFunction.condOpt(push.notification) {
      case ca: ContentNotification => toContent(ca)
      case bn: BreakingNewsNotification => toBreakingNews(bn)
      case el: ElectionNotification => toElectionAlert(el)
      case mi: LiveEventNotification => toLiveEventAlert(mi)
      case ms: FootballMatchStatusNotification => toMatchStatusAlert(ms)
    }
    firebaseAndroidNotification.map(_.toAndroidConfig)
  }

  private case class FirebaseAndroidNotification(
    data: Map[String, String]
  ) {
    def toAndroidConfig: AndroidConfig =
      AndroidConfig.builder()
        .putAllData(data.asJava)
        .setPriority(AndroidConfig.Priority.HIGH)
        .setTtl(86400000L) // 24 hours
        .build()
  }


  private def toAndroidTopic(topic: Topic) = s"${topic.`type`}//${topic.name}"

  private def toBreakingNews(breakingNews: BreakingNewsNotification): FirebaseAndroidNotification = {

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
    val patformLink = toPlatformLink(breakingNews.link)
    val edition = if (editions.size == 1) Some(editions.head) else None
    val keyword = tagLink.map(new URI(_))

    FirebaseAndroidNotification(
      Map(
        Keys.NotificationType -> AndroidMessageTypes.Custom,
        Keys.UniqueIdentifier -> breakingNews.id.toString,
        Keys.Type -> AndroidMessageTypes.Custom,
        Keys.Title -> breakingNews.title,
        Keys.Ticker -> breakingNews.message,
        Keys.Message -> breakingNews.message,
        Keys.Debug -> conf.debug.toString,
        Keys.Editions -> editions.mkString(","),
        Keys.Link -> toAndroidLink(breakingNews.link).toString,
        Keys.UriType -> patformLink.`type`,
        Keys.Uri -> patformLink.uri
      ) ++ Map(
        Keys.Section -> sectionLink.map(new URI(_)).map(_.toString),
        Keys.Edition -> edition.map(_.toString),
        Keys.Keyword -> keyword.map(_.toString),
        Keys.ImageUrl -> breakingNews.imageUrl.map(_.toString),
        Keys.ThumbnailUrl -> breakingNews.thumbnailUrl.map(_.toString)
      ).flattenValues
    )
  }

  private def toContent(cn: ContentNotification): FirebaseAndroidNotification = {
    val link = toPlatformLink(cn.link)

    FirebaseAndroidNotification(
      Map(
        Keys.Type -> AndroidMessageTypes.Custom,
        Keys.UniqueIdentifier -> cn.id.toString,
        Keys.Title -> cn.title,
        Keys.Ticker -> cn.message,
        Keys.Message -> cn.message,
        Keys.Link -> toAndroidLink(cn.link).toString,
        Keys.Topics -> cn.topic.map(toAndroidTopic).mkString(","),
        Keys.UriType -> link.`type`,
        Keys.Uri -> new URI(link.uri).toString,
        Keys.Debug -> conf.debug.toString
      ) ++ Map(
        Keys.ThumbnailUrl -> cn.thumbnailUrl.map(_.toString)
      ).flattenValues
    )
  }

  private def toElectionAlert(electionAlert: ElectionNotification): FirebaseAndroidNotification = {
    def resultsFlattened: List[(String, String)] = {
      val data = electionAlert.results.candidates.zipWithIndex.flatMap { case (candidate, index) =>
        List(
          s"candidates[$index].name" -> candidate.name,
          s"candidates[$index].electoralVotes" -> candidate.electoralVotes.toString,
          s"candidates[$index].color" -> candidate.color
        ) ++ candidate.avatar.map({ avatar => s"candidates[$index].avatar" -> avatar.toString }).toList
      }
      val length = "candidates.length" -> electionAlert.results.candidates.size.toString
      length :: data
    }

    FirebaseAndroidNotification(
      Map(
        Keys.Type -> AndroidMessageTypes.ElectionAlert,
        Keys.UniqueIdentifier -> electionAlert.id.toString,
        Keys.ExpandedMessage -> electionAlert.expandedMessage.getOrElse(electionAlert.message),
        Keys.ShortMessage -> electionAlert.shortMessage.getOrElse(electionAlert.message),
        Keys.Debug -> conf.debug.toString,
        Keys.ElectoralCollegeSize -> "538",
        Keys.Link -> toAndroidLink(electionAlert.link).toString,
        Keys.ResultsLink -> toAndroidLink(electionAlert.resultsLink).toString,
        Keys.Title -> electionAlert.title,
        Keys.Importance -> electionAlert.importance.toString
      ) ++ resultsFlattened.toMap
    )
  }

  private def toLiveEventAlert(innovationAlert: LiveEventNotification): FirebaseAndroidNotification = {
    FirebaseAndroidNotification(
      Map(
        Keys.UniqueIdentifier -> Some(innovationAlert.id.toString),
        Keys.Message -> Some(innovationAlert.message),
        Keys.ShortMessage -> Some(innovationAlert.shortMessage.getOrElse(innovationAlert.message)),
        Keys.ExpandedMessage -> Some(innovationAlert.expandedMessage.getOrElse(innovationAlert.message)),
        Keys.Importance -> Some(innovationAlert.importance.toString),
        Keys.Link1 -> Some(toAndroidLink(innovationAlert.link1).toString),
        Keys.Link2 -> Some(toAndroidLink(innovationAlert.link2).toString),
        Keys.ImageUrl -> innovationAlert.imageUrl.map(_.toString),
        Keys.Topics -> Some(innovationAlert.topic.map(toAndroidTopic).mkString(",")),
        Keys.Debug -> Some(conf.debug.toString),
        Keys.Title -> Some(innovationAlert.title),
        Keys.Type -> Some(AndroidMessageTypes.SuperbowlEvent)
      ).flattenValues
    )
  }

  private def toMatchStatusAlert(matchStatusAlert: FootballMatchStatusNotification): FirebaseAndroidNotification = FirebaseAndroidNotification(
    Map(
      "type" -> Some(AndroidMessageTypes.FootballMatchAlert),
      "homeTeamName" -> Some(matchStatusAlert.homeTeamName),
      "homeTeamId" -> Some(matchStatusAlert.homeTeamId),
      "homeTeamScore" -> Some(matchStatusAlert.homeTeamScore.toString),
      "homeTeamText" -> Some(matchStatusAlert.homeTeamMessage),
      "awayTeamName" -> Some(matchStatusAlert.awayTeamName),
      "awayTeamId" -> Some(matchStatusAlert.awayTeamId),
      "awayTeamScore" -> Some(matchStatusAlert.awayTeamScore.toString),
      "awayTeamText" -> Some(matchStatusAlert.awayTeamMessage),
      "currentMinute" -> Some(""),
      "important" -> Some(matchStatusAlert.importance.toString),
      "matchStatus" -> Some(matchStatusAlert.phase),
      "matchId" -> Some(matchStatusAlert.matchId),
      "matchInfoUri" -> Some(matchStatusAlert.matchInfoUri.toString),
      "articleUri" -> matchStatusAlert.articleUri.map(_.toString),
      "competitionName" -> matchStatusAlert.competitionName,
      "venue" -> matchStatusAlert.venue
    ).flattenValues
  )

  protected def replaceHost(uri: URI): String = List(Some("x-gu://"), Option(uri.getPath), Option(uri.getQuery).map("?" + _)).flatten.mkString

  case class PlatformUri(uri: String, `type`: String)

  protected def toPlatformLink(link: Link): PlatformUri = link match {
    case Link.Internal(contentApiId, _, _) => PlatformUri(s"x-gu:///items/$contentApiId", "item")
    case Link.External(url) => PlatformUri(url, "external")
  }

  private def toAndroidLink(link: Link) = link match {
    case Link.Internal(contentApiId, _, _) => new URI(s"x-gu://www.guardian.co.uk/$contentApiId")
    case Link.External(url) => new URI(url)
  }

  private[services] def toTags(destination: Destination) = destination match {
    case Left(topics: Set[Topic]) => Some(Tags.fromTopics(topics))
    case Right(user: UniqueDeviceIdentifier) => Some(Tags.fromUserId(user))
  }
}