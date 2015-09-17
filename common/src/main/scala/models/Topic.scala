package models

import play.api.libs.json._

case class Topic(`type`: TopicType, name: String)

sealed trait TopicType

object TopicTypes {
  case object Content extends TopicType { override def toString = "content" }
  case object TagContributor extends TopicType { override def toString = "tag-contributor" }
  case object TagKeyword extends TopicType { override def toString = "tag-keyword" }
  case object TagSeries extends TopicType { override def toString = "tag-series" }
  case object TagBlog extends TopicType { override def toString = "tag-blog" }
  case object FootballTeam extends TopicType { override def toString = "football-team" }
  case object FootballMatch extends TopicType { override def toString = "football-match" }
}

object TopicType {
  import TopicTypes._
  def fromString(s: String): Option[TopicType] = s match {
    case "content" => Some(Content)
    case "tag-contributor" => Some(TagContributor)
    case "tag-keyword" => Some(TagKeyword)
    case "tag-series" => Some(TagSeries)
    case "tag-blog" => Some(TagBlog)
    case "football-team" => Some(FootballTeam)
    case "football-match" => Some(FootballMatch)
  }

  implicit val jf = new Format[TopicType] {
    def reads(json: JsValue): JsResult[TopicType] = json match {
      case JsString(s) => fromString(s) map { JsSuccess(_) } getOrElse JsError(s"$s is not a valid topic type")
      case _ => JsError(s"Topic type could not be decoded")
    }

    def writes(obj: TopicType): JsValue = JsString(obj.toString)
  }
}
