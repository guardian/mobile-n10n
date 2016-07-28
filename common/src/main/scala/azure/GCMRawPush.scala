package azure

import play.api.libs.json.Json

case class GCMRawPush(body: GCMBody, tags: Option[Tags]) {
  def tagQuery: Option[String] = tags.map { set =>
    set.tags.map(_.encodedTag).mkString("(", " || ", ")")
  }
}

case class GCMBody(
  collapse_key: Option[String] = None,
  time_to_live: Option[String] = None,
  delay_while_idle: Option[Boolean] = None,
  data: Map[String, String]
)
object GCMBody {
  implicit val jf = Json.format[GCMBody]
}
