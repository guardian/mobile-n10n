package com.gu.notifications.worker.delivery.apns.models.payload

sealed trait CustomProperty { def key: String }
case class CustomPropertyString(key: String, value: String) extends CustomProperty
case class CustomPropertySeq(key: String, value: Seq[CustomPropertyString]) extends CustomProperty

object CustomProperty {

  def apply(tuple: (String, String)): CustomPropertyString =
    CustomPropertyString(tuple._1, tuple._2)
  def apply(tuple: (String, Seq[CustomPropertyString])): CustomPropertySeq =
    CustomPropertySeq(tuple._1, tuple._2)

  object Keys {
    final val MessageType = "t"
    final val NotificationType = "notificationType"
    final val Link = "link"
    final val Topics = "topics"
    final val Uri = "uri"
    final val UriType = "uriType"
    final val ImageUrl = "imageUrl"
    final val Provider = "provider"
    final val UniqueIdentifier = "uniqueIdentifier"
    final val FootballMatch = "footballMatch"
    final val HomeTeamName = "homeTeamName"
    final val HomeTeamId = "homeTeamId"
    final val HomeTeamScore = "homeTeamScore"
    final val HomeTeamText = "homeTeamText"
    final val AwayTeamName = "awayTeamName"
    final val AwayTeamId = "awayTeamId"
    final val AwayTeamScore = "awayTeamScore"
    final val AwayTeamText = "awayTeamText"
    final val CurrentMinute = "currentMinute"
    final val MatchStatus = "matchStatus"
    final val MatchId = "matchId"
    final val MapiUrl = "mapiUrl"
    final val MatchInfoUri = "matchInfoUri"
    final val ArticleUri = "articleUri"
    final val CompetitionName = "competitionName"
    final val Venue = "venue"
  }
}



