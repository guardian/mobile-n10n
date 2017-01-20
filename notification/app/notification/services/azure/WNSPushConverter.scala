package notification.services.azure

import java.net.URI

import _root_.azure.{Tags, WNSRawPush}
import models.Link.{External, Internal}
import models._
import notification.models.Destination.Destination
import notification.models.{Push, wns}
import notification.services.Configuration
import play.api.Logger
import play.api.libs.json.Json
import PartialFunction.condOpt

class WNSPushConverter(conf: Configuration) extends PushConverter {
  val logger = Logger(classOf[WNSPushConverter])

  def toRawPush(push: Push): Option[WNSRawPush] = {
    logger.debug(s"Converting push to Azure: $push")
    toAzure(push.notification) map { notification =>
      WNSRawPush(
        body = Json.stringify(Json.toJson(notification)),
        tags = toTags(push.destination)
      )
    }
  }

  private[services] def toAzure(notification: Notification): Option[wns.Notification] = condOpt(notification) {
    case bnn: BreakingNewsNotification => toBreakingNews(bnn)
    case cn: ContentNotification => toContent(cn)
    case gan: GoalAlertNotification => toGoalAlert(gan)
    case el: ElectionNotification => toElectionResult(el)
  }

  private[services] def toTags(destination: Destination) = destination match {
    case Left(topics: Set[Topic]) => Some(Tags.fromTopics(topics))
    case Right(user: UniqueDeviceIdentifier) => Some(Tags.fromUserId(user))
  }

  private def toUrl(link: Link): URI = link match {
    case External(url) => new URI(url)
    case Internal(capiId, _, _) => new URI(s"${conf.mapiItemEndpoint}/$capiId")
  }

  private def toBreakingNews(bnn: BreakingNewsNotification) = wns.BreakingNewsNotification(
    id = bnn.id,
    `type` = bnn.`type`,
    title = bnn.title,
    message = bnn.message,
    thumbnailUrl = bnn.thumbnailUrl,
    link = toUrl(bnn.link),
    imageUrl = bnn.imageUrl,
    topic = bnn.topic,
    debug = conf.debug
  )

  private def toContent(cn: ContentNotification) = wns.ContentNotification(
    id = cn.id,
    `type` = cn.`type`,
    title = cn.title,
    message = cn.message,
    thumbnailUrl = cn.thumbnailUrl,
    link = toUrl(cn.link),
    topic = cn.topic,
    debug = conf.debug
  )

  private def toGoalAlert(gan: GoalAlertNotification) = wns.GoalAlertNotification(
    id = gan.id,
    `type` = gan.`type`,
    title = gan.title,
    message = gan.message,
    thumbnailUrl = gan.thumbnailUrl,
    goalType = gan.goalType,
    awayTeamName = gan.awayTeamName,
    awayTeamScore = gan.awayTeamScore,
    homeTeamName = gan.homeTeamName,
    homeTeamScore = gan.homeTeamScore,
    scoringTeamName = gan.scoringTeamName,
    scorerName = gan.scorerName,
    goalMins = gan.goalMins,
    otherTeamName = gan.otherTeamName,
    matchId = gan.matchId,
    link = gan.mapiUrl,
    topic = gan.topic,
    addedTime = gan.addedTime,
    debug = conf.debug
  )

  private def toElectionResult(el: ElectionNotification) = wns.ElectionNotification(
    id = el.id,
    title = el.message,
    message = el.message,
    thumbnailUrl = None,
    link = toUrl(el.link),
    imageUrl = None,
    topic = Set.empty,
    debug = conf.debug
  )
}
