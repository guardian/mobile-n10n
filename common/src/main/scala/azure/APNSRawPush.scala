package azure

import azure.apns.Body

case class APNSRawPush(body: Body, tags: Option[Tags]) {
  def tagQuery: Option[String] = tags.map { set =>
    set.tags.map(_.encodedTag).mkString("(", " || ", ")")
  }
}
