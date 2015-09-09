package gu.msnotifications

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class AzureXmlPush(wnsType: String, xml: scala.xml.Elem, topics: Option[Set[Topic]]) {
  def tagQuery: Option[String] = topics.map { set =>
    set.map(_.toUri).mkString("(", " && ", ")")
  }
}

object AzureXmlPush {
  /**
   * [[https://msdn.microsoft.com/library/windows/apps/hh465435.aspx#pncodes_x_wns_type]]
   */
  val validTypes = Set("wns/toast", "wns/badge", "wns/tile")

  implicit val topicReads = Json.reads[Topic]

  implicit val reads = (
    (__ \ "wnsType").read[String] and
      (__ \ "topics").readNullable[Set[Topic]] and
      (__ \ "xml").read[String]
    ) { (wnsType, topics, xml) =>
    AzureXmlPush(
      wnsType = wnsType,
      topics = topics,
      xml = scala.xml.XML.loadString(xml)
    )
  }

}