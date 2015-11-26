package models

import play.api.libs.json._

case class URL(url: String)

object URL {
  implicit val jf = new Format[URL] {
    override def writes(o: URL): JsValue = JsString(o.url)
    override def reads(json: JsValue): JsResult[URL] = json match {
      case JsString(url) => JsSuccess(URL(url))
      case _ => JsError("unknown url type")
    }
  }
}
