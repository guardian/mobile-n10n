package gu.msnotifications

import models.{Push, Topic}

case class AzureXmlPush(wnsType: String, xml: scala.xml.Elem, topics: Option[Set[Topic]]) {
  def tagQuery: Option[String] = topics.map { set =>
    set.map(_.toWNSUri).mkString("(", " && ", ")")
  }
}

object AzureXmlPush {
  /**
   * [[https://msdn.microsoft.com/library/windows/apps/hh465435.aspx#pncodes_x_wns_type]]
   */
  val validTypes = Set("wns/toast", "wns/badge", "wns/tile")

  def fromPush(p: Push) = AzureXmlPush(p.wnsType, p.xml, p.topics)
}