package models

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class Push(wnsType: String, xml: scala.xml.Elem, topics: Option[Set[Topic]]) {
  def tagQuery: Option[String] = None
}

object Push {

  implicit val topicReads = Json.reads[Topic]

  implicit val reads = (
    (__ \ "wnsType").read[String] and
      (__ \ "topics").readNullable[Set[Topic]] and
      (__ \ "xml").read[String]
    ) { (wnsType, topics, xml) =>
    Push(
      wnsType = wnsType,
      topics = topics,
      xml = scala.xml.XML.loadString(xml)
    )
  }

}