package models

import play.api.libs.json._

sealed trait TopicType

object TopicTypes {
  case object Breaking extends TopicType { override def toString: String = "breaking" }
  case object Content extends TopicType { override def toString: String = "content" }
  case object TagContributor extends TopicType { override def toString: String = "tag-contributor" }
  case object TagKeyword extends TopicType { override def toString: String = "tag-keyword" }
  case object TagSeries extends TopicType { override def toString: String = "tag-series" }
  case object TagBlog extends TopicType { override def toString: String = "tag-blog" }
  case object FootballTeam extends TopicType { override def toString: String = "football-team" }
  case object FootballMatch extends TopicType { override def toString: String = "football-match" }
  case object Newsstand extends TopicType { override def toString: String = "newsstand" }
}

object TopicType {
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
    case "newsstand" => Newsstand
  }

  implicit val jf = new Format[TopicType] {
    def reads(json: JsValue): JsResult[TopicType] = json match {
      case JsString(s) => fromString(s) map { JsSuccess(_) } getOrElse JsError(s"$s is not a valid topic type")
      case _ => JsError(s"Topic type could not be decoded")
    }

    def writes(obj: TopicType): JsValue = JsString(obj.toString)
  }
}
