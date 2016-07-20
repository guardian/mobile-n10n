package registration.models

import play.api.libs.json.{Json, Reads}
import LegacyJodaFormat._

object LegacyJodaFormat {
  val DateTimePattern = "yyyy-MM-dd'T'HH:mm:ss'Z'"
  implicit val dateTimeFormat = Reads.jodaDateReads(DateTimePattern)
}

object LegacyTopic {
  val FootballTeamType = "football-team"
  val FootballMatchType = "football-match"
  val UserType = "user-type"

  val NewsstandIos = LegacyTopic(`type` = "newsstand", `name` = "newsstandIos")

  implicit val jf = Json.format[LegacyTopic]
}

case class LegacyTopic(
  `type`: String,
  name: String
) {
  def toTopicString: String = `type` + "//" + name
}

case class LegacyDevice(platform: String, udid: String, pushToken: String, buildTier: String)
object LegacyDevice {
  implicit val jf = Json.format[LegacyDevice]
}

case class LegacyMatch(matchId: String, matchDate: String)
object LegacyMatch {
  implicit val jf = Json.format[LegacyMatch]
}

case class LegacyPreferences(
  receiveNewsAlerts: Boolean,
  edition: String,
  teams: Option[Seq[String]],
  matches: Option[Seq[LegacyMatch]],
  topics: Option[Seq[LegacyTopic]]
) {
  def hasNewsstand: Boolean = topics.exists(_.contains(LegacyTopic.NewsstandIos))
  def withNewsstand: LegacyPreferences = {
    if (hasNewsstand)
      this
    else
      copy( topics = Option { topics.toList.flatten :+ LegacyTopic.NewsstandIos } )
  }
}
object LegacyPreferences {
  implicit val jf = Json.format[LegacyPreferences]
}

case class LegacyRegistration(device: LegacyDevice, preferences: LegacyPreferences)
object LegacyRegistration {
  implicit val jf = Json.format[LegacyRegistration]
}