package notification.services

import java.net.URL

import azure.{AzureRawPush, Tag}
import models.Link.{External, Internal}
import models._
import notification.models.azure
import play.api.libs.json.Json
import models.JsonUtils._

class AzureRawPushConverter(conf: Configuration) {
  def toAzureRawPush(push: Push): AzureRawPush = {
    val azureNotification = push.notification match {
      case bnn: BreakingNewsNotification => toAzureBreakingNews(bnn)
      case cn: ContentNotification => toContent(cn)
      case gan: GoalAlertNotification => toGoalAlert(gan)
    }
    AzureRawPush(
      body = Json.stringify(Json.toJson(azureNotification)),
      tags = tags(push.destination)
    )
  }

  private def tags(destination: Either[Topic, UserId]) = destination match {
    case Left(topic: Topic) => Some(Set(Tag.fromTopic(topic)))
    case Right(user: UserId) => Some(Set(Tag.fromUserId(user)))
  }

  private def toUrl(link: Link): URL = link match {
    case External(url) => new URL(url)
    case Internal(capiId) => new URL(s"${conf.mapiItemEndpoint}/$capiId")
  }

  private def toAzureBreakingNews(bnn: BreakingNewsNotification) = azure.BreakingNewsNotification(
    id = bnn.id,
    `type` = bnn.`type`,
    title = bnn.title,
    message = bnn.message,
    thumbnailUrl = bnn.thumbnailUrl,
    link = toUrl(bnn.link),
    imageUrl = bnn.imageUrl,
    topic = bnn.topic
  )

  private def toContent(cn: ContentNotification) = azure.ContentNotification(
    id = cn.id,
    `type` = cn.`type`,
    title = cn.title,
    message = cn.message,
    thumbnailUrl = cn.thumbnailUrl,
    link = toUrl(cn.link),
    topic = cn.topic
  )

  private def toGoalAlert(gan: GoalAlertNotification) = azure.GoalAlertNotification(
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
    link = new URL(gan.mapiUrl),
    topic = gan.topic,
    addedTime = gan.addedTime
  )
}
