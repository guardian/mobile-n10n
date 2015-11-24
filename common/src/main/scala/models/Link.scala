package models

import play.api.libs.json._

sealed trait Link
object Link {
  case class External(url: String) extends Link
  case class Internal(contentApiId: String) extends Link

  implicit val jf = new Format[Link] {
    override def writes(o: Link): JsValue = o match {
      case External(url) => Json.obj("url" -> url)
      case Internal(contentApiId: String) => Json.obj("contentApiId" -> contentApiId)
    }

    override def reads(json: JsValue): JsResult[Link] = {
      (json \ "contentApiId", json \ "url") match {
        case (JsDefined(JsString(contentApiId)), _) => JsSuccess(Internal(contentApiId))
        case (_, JsDefined(JsString(url))) => JsSuccess(External(url))
        case _ => JsError("Unknown link type")
      }
    }
  }
}
