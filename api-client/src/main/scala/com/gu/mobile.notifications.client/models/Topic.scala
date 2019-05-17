package com.gu.mobile.notifications.client.models

import com.gu.mobile.notifications.client.models.Editions._
import com.gu.mobile.notifications.client.models.TopicTypes._
import play.api.libs.json._

sealed trait TopicType

object TopicType {
  implicit val writes = new Writes[TopicType] {
    override def writes(o: TopicType): JsValue = JsString(o.toString)
  }

  implicit val reads = new Reads[TopicType] {
    override def reads(json: JsValue): JsResult[TopicType] = json match {
      case JsString("breaking") => JsResult.applicativeJsResult.pure(Breaking)
      case JsString("content") => JsResult.applicativeJsResult.pure(Content)
      case JsString("tag-contributor") => JsResult.applicativeJsResult.pure(TagContributor)
      case JsString("tag-keyword") => JsResult.applicativeJsResult.pure(TagKeyword)
      case JsString("tag-series") => JsResult.applicativeJsResult.pure(TagSeries)
      case JsString("tag-blog") => JsResult.applicativeJsResult.pure(TagBlog)
      case JsString("football-team") => JsResult.applicativeJsResult.pure(FootballTeam)
      case JsString("football-match") => JsResult.applicativeJsResult.pure(FootballMatch)
      case JsString("user-type") => JsResult.applicativeJsResult.pure(User)
      case JsString("newsstand") => JsResult.applicativeJsResult.pure(Newsstand)
      case _ => JsError(s"Unknown topic type: $json")
    }
  }

}

object TopicTypes {
  case object Breaking extends TopicType { override val toString = "breaking" }
  case object Content extends TopicType { override val toString = "content" }
  case object TagContributor extends TopicType { override val toString = "tag-contributor" }
  case object TagKeyword extends TopicType { override val toString = "tag-keyword" }
  case object TagSeries extends TopicType { override val toString = "tag-series" }
  case object TagBlog extends TopicType { override val toString = "tag-blog" }
  case object FootballTeam extends TopicType { override val toString = "football-team" }
  case object FootballMatch extends TopicType { override val toString = "football-match" }
  case object User extends TopicType { override val toString = "user-type" }
  case object Newsstand extends TopicType { override val toString = "newsstand" }
}

case class Topic(`type`: TopicType, name: String) {
  def toTopicString = `type`.toString + "//" + name
}
object Topic {
  implicit val writes = Json.writes[Topic]
  implicit val reads = Json.reads[Topic]
  val BreakingNewsUk = Topic(Breaking, UK.toString)
  val BreakingNewsUs = Topic(Breaking, US.toString)
  val BreakingNewsAu = Topic(Breaking, AU.toString)
  val BreakingNewsInternational = Topic(Breaking, International.toString)
  val BreakingNewsSport = Topic(Breaking, "sport")
  val BreakingNewsInternalTest = Topic(Breaking, "internal-test")
  val NewsstandIos = Topic(Newsstand, "newsstandIos")
}

