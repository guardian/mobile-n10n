package models

import play.api.libs.json._

sealed trait Importance
object Importance {
  case object Minor extends Importance
  case object Major extends Importance

  implicit val jf = new Format[Importance] {
    override def writes(o: Importance): JsValue = o match {
      case Minor => JsString("Minor")
      case Major => JsString("Major")
    }

    override def reads(json: JsValue): JsResult[Importance] = json match {
      case JsString("Major") => JsSuccess(Major)
      case JsString("Minor") => JsSuccess(Minor)
      case _ => JsError("Unknown priority")
    }
  }
}