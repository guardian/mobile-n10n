package notification.services.fcm

import java.net.URI

import _root_.azure.Tags
import com.google.firebase.messaging.AndroidConfig
import models._
import notification.models.Destination._
import notification.models.android.Editions.Edition
import notification.models.android.{AndroidMessageTypes, Keys}
import notification.models.Push
import notification.services.Configuration
import play.api.Logger
import utils.MapImplicits._

import scala.PartialFunction._
import collection.JavaConverters._

class AndroidConfigConverter(conf: Configuration) {

  val logger = Logger(classOf[AndroidConfigConverter])

  def toAndroidConfig(push: Push): Option[AndroidConfig] = toFirebaseAndroidNotification(push).map(_.toAndroidConfig)

  case class FirebaseAndroidNotification(
    data: Map[String, String]
  ) {
    def toAndroidConfig: AndroidConfig =
      AndroidConfig.builder()
        .putAllData(data.asJava)
        .setPriority(AndroidConfig.Priority.HIGH)
        .setTtl(86400000L) // 24 hours
        .build()
  }

  def toFirebaseAndroidNotification(push: Push): Option[FirebaseAndroidNotification] = {
    logger.debug(s"Converting push to android FCM: $push")
    PartialFunction.condOpt(push.notification) {
      case ca: ContentNotification => toContent(ca)
      case bn: BreakingNewsNotification => toBreakingNews(bn)
      case ms: FootballMatchStatusNotification => toMatchStatusAlert(ms)
    }
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
    val platformLink = toPlatformLink(breakingNews.link)
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
        Keys.UriType -> platformLink.`type`,
        Keys.Uri -> platformLink.uri
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

  private def toMatchStatusAlert(matchStatusAlert: FootballMatchStatusNotification): FirebaseAndroidNotification = FirebaseAndroidNotification(
    Map(
      "type" -> AndroidMessageTypes.FootballMatchAlert,
      "homeTeamName" -> matchStatusAlert.homeTeamName,
      "homeTeamId" -> matchStatusAlert.homeTeamId,
      "homeTeamScore" -> matchStatusAlert.homeTeamScore.toString,
      "homeTeamText" -> matchStatusAlert.homeTeamMessage,
      "awayTeamName" -> matchStatusAlert.awayTeamName,
      "awayTeamId" -> matchStatusAlert.awayTeamId,
      "awayTeamScore" -> matchStatusAlert.awayTeamScore.toString,
      "awayTeamText" -> matchStatusAlert.awayTeamMessage,
      "currentMinute" -> "",
      "important" -> matchStatusAlert.importance.toString,
      "matchStatus" -> matchStatusAlert.matchStatus,
      "matchId" -> matchStatusAlert.matchId,
      "matchInfoUri" -> matchStatusAlert.matchInfoUri.toString
    ) ++ Map(
      "articleUri" -> matchStatusAlert.articleUri.map(_.toString),
      "competitionName" -> matchStatusAlert.competitionName,
      "venue" -> matchStatusAlert.venue
    ).flattenValues
  )

  case class PlatformUri(uri: String, `type`: String)

  protected def toPlatformLink(link: Link): PlatformUri = link match {
    case Link.Internal(contentApiId, _, _) => PlatformUri(s"x-gu:///items/$contentApiId", "item")
    case Link.External(url) => PlatformUri(url, "external")
  }

  private def toAndroidLink(link: Link) = link match {
    case Link.Internal(contentApiId, _, _) => new URI(s"x-gu://www.guardian.co.uk/$contentApiId")
    case Link.External(url) => new URI(url)
  }

  private[services] def toTags(destination: Destination) = Some(Tags.fromTopics(destination))
}