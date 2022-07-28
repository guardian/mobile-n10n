package com.gu.notifications.workerlambda.models

import play.api.libs.json._

case class TopicType(name: String, priority: Int) {
  override def toString: String = name
}

object TopicTypes {
  object Breaking extends TopicType("breaking", 100)
  object Content extends TopicType("content", 1)
  object TagContributor extends TopicType("tag-contributor", 1)
  object TagKeyword extends TopicType("tag-keyword", 1)
  object TagSeries extends TopicType("tag-series", 1)
  object TagBlog extends TopicType("tag-blog", 1)
  object FootballTeam extends TopicType("football-team", 1)
  object FootballMatch extends TopicType("football-match", 1)
  object Newsstand extends TopicType("newsstand", 100)
  object NewsstandShard extends TopicType("newsstand-shard", 100)
  object Editions extends TopicType("editions", 100)
  object LiveNotification extends TopicType("live-notification", 51)
}

object TopicType {
  val newsstandShardPattern = "newsstand-(\\d+)".r
  import TopicTypes._
  def fromString(s: String): Option[TopicType] = PartialFunction.condOpt(s) {
    case "breaking" => Breaking
    case "content" => Content
    case "tag-contributor" => TagContributor
    case "tag-keyword" => TagKeyword
    case "tag-series" => TagSeries
    case "tag-blog" => TagBlog
    case "football-team" => FootballTeam
    case "football-match" => FootballMatch
    case "newsstand" => TopicTypes.Newsstand
    case "newsstand-shard" => TopicTypes.NewsstandShard
    case "live-notification" => LiveNotification
    case "editions" => Editions
  }

  implicit val jf = new Format[TopicType] {
    def reads(json: JsValue): JsResult[TopicType] = json match {
      case JsString(s) => fromString(s) map { JsSuccess(_) } getOrElse JsError(s"$s is not a valid topic type")
      case _ => JsError(s"Topic type could not be decoded")
    }

    def writes(obj: TopicType): JsValue = JsString(obj.toString)
  }
}
