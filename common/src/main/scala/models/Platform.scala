package models

import play.api.libs.json._

sealed trait Platform

case object Android extends Platform { override def toString = "android" }
case object iOS extends Platform { override def toString = "ios" }
case object WindowsMobile extends Platform { override def toString = "windows-mobile" }

object Platform {
  def fromString(s: String): Option[Platform] = s match {
    case "android" => Some(Android)
    case "ios" => Some(iOS)
    case "windows-mobile" => Some(WindowsMobile)
  }

  implicit val jf = new Format[Platform] {
    def reads(json: JsValue): JsResult[Platform] = json match {
      case JsString(s) => fromString(s) map { JsSuccess(_) } getOrElse JsError(s"$s is not a valid topic type")
      case _ => JsError(s"Topic type could not be decoded")
    }

    def writes(obj: Platform): JsValue = JsString(obj.toString)
  }
}
