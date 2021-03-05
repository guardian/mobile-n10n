package com.gu.mobile.notifications.client.models

import com.gu.mobile.notifications.client.models.Editions._
import com.gu.mobile.notifications.client.models.TopicTypes._
import play.api.libs.json._

sealed trait TopicType

object TopicType {
  implicit val jf = new Writes[TopicType] {
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
  implicit val jf = Json.writes[Topic]
  val BreakingNewsUk = Topic(Breaking, UK.toString)
  val BreakingNewsUs = Topic(Breaking, US.toString)
  val BreakingNewsAu = Topic(Breaking, AU.toString)
  val BreakingNewsInternational = Topic(Breaking, International.toString)
  val BreakingNewsSport = Topic(Breaking, "sport")
  val BreakingNewsSportUk = Topic(Breaking, "sport-uk")
  val BreakingNewsSportUs = Topic(Breaking, "sport-us")
  val BreakingNewsSportAu = Topic(Breaking, "sport-au")
  val BreakingNewsSportInternational = Topic(Breaking, "sport-international")
  val BreakingNewsElection = Topic(Breaking, "uk-general-election")
  val BreakingNewsUSElection = Topic(Breaking, "us-election-2020-live")
  val BreakingNewsCovid19Uk = Topic(Breaking, "uk-covid-19")
  val BreakingNewsCovid19Us = Topic(Breaking, "us-covid-19")
  val BreakingNewsCovid19Au = Topic(Breaking, "au-covid-19")
  val BreakingNewsCovid19International = Topic(Breaking, "international-covid-19")
  val BreakingNewsInternalTest = Topic(Breaking, "internal-test")
  val NewsstandIos = Topic(Newsstand, "newsstandIos")
}

