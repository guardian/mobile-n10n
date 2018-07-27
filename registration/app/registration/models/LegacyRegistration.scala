package registration.models

import play.api.libs.json._
import play.api.libs.json.JodaReads._
import LegacyJodaFormat._

object LegacyJodaFormat {
  val DateTimePattern = "yyyy-MM-dd'T'HH:mm:ss'Z'"
  implicit val dateTimeFormat = JodaReads.jodaDateReads(DateTimePattern)
}

object LegacyTopic {
  val FootballTeamType = "football-team"
  val FootballMatchType = "football-match"
  val UserType = "user-type"

  val NewsstandIos = LegacyTopic(`type` = "newsstand", `name` = "newsstandIos")

  implicit val jf = new Format[LegacyTopic] {
    override def reads(json: JsValue): JsResult[LegacyTopic] = for {
      tp <- (json \ "type").validate[String]
      name <- (json \ "id").validate[String].orElse((json \ "name").validate[String])
    } yield LegacyTopic(tp, name)

    private val writer = Json.writes[LegacyTopic]
    override def writes(o: LegacyTopic): JsValue = writer.writes(o)
  }
}

case class LegacyTopic(
  `type`: String,
  name: String
) {
  def toTopicString: String = `type` + "//" + name
}

case class LegacyDevice(
  platform: String,
  pushToken: Option[String],
  firebaseToken: Option[String],
  buildTier: String
)
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