package com.gu.mobile.notifications.client.models

import com.gu.mobile.notifications.client.models.Editions._
import com.gu.mobile.notifications.client.models.TopicTypes._
import play.api.libs.json._

sealed trait TopicType

object TopicType {
  implicit val jf: Writes[TopicType] = new Writes[TopicType] {
    override def writes(o: TopicType): JsValue = JsString(o.toString)
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
  implicit val jf: OWrites[Topic] = Json.writes[Topic]
  val BreakingNewsUk = Topic(Breaking, UK.toString)
  val BreakingNewsUs = Topic(Breaking, US.toString)
  val BreakingNewsAu = Topic(Breaking, AU.toString)
  val BreakingNewsInternational = Topic(Breaking, International.toString)
  val BreakingNewsEurope = Topic(Breaking, Europe.toString)
  val BreakingNewsSportUk = Topic(Breaking, "uk-sport")
  val BreakingNewsSportUs = Topic(Breaking, "us-sport")
  val BreakingNewsSportAu = Topic(Breaking, "au-sport")
  val BreakingNewsSportInternational = Topic(Breaking, "international-sport")
  val BreakingNewsSportEurope = Topic(Breaking, "europe-sport")
  val BreakingNewsElection = Topic(Breaking, "uk-general-election")
  val BreakingNewsUSElection = Topic(Breaking, "us-election-2020-live")
  val BreakingNewsInternalTest = Topic(Breaking, "internal-test")
  val NewsstandIos = Topic(Newsstand, "newsstandIos")
}

