package models

import play.api.libs.json._

case class TopicType(name: String, priority: Int) {
  override def toString: String = name
}

object TopicTypes {
  object Breaking extends TopicType("breaking", 100) // scalastyle:off magic.number
  object Content extends TopicType("content", 1)
  object TagContributor extends TopicType("tag-contributor", 1)
  object TagKeyword extends TopicType("tag-keyword", 1)
  object TagSeries extends TopicType("tag-series", 1)
  object TagBlog extends TopicType("tag-blog", 1)
  object FootballTeam extends TopicType("football-team", 1)
  object FootballMatch extends TopicType("football-match", 1)
  object Newsstand extends TopicType("newsstand", 100) // scalastyle:off magic.number
  object ElectionResults extends TopicType("election-results", 50) // scalastyle:off magic.number
  object LiveNotification extends TopicType("live-notification", 51) // scalastyle:off magic.number
}

object TopicType {
  import TopicTypes._
  def fromString(s: String): Option[TopicType] = PartialFunction.condOpt(s) { // scalastyle:off cyclomatic.complexity
    case "breaking" => Breaking
    case "content" => Content
    case "tag-contributor" => TagContributor
    case "tag-keyword" => TagKeyword
    case "tag-series" => TagSeries
    case "tag-blog" => TagBlog
    case "football-team" => FootballTeam
    case "football-match" => FootballMatch
    case "newsstand" => Newsstand
    case "election-results" => ElectionResults
    case "live-notification" => LiveNotification
  }

  implicit val jf = new Format[TopicType] {
    def reads(json: JsValue): JsResult[TopicType] = json match {
      case JsString(s) => fromString(s) map { JsSuccess(_) } getOrElse JsError(s"$s is not a valid topic type")
      case _ => JsError(s"Topic type could not be decoded")
    }

    def writes(obj: TopicType): JsValue = JsString(obj.toString)
  }
}
