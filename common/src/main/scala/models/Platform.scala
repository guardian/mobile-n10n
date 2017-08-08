package models

import play.api.libs.json._

sealed trait Platform

case object Android extends Platform { override def toString: String = "android" }
case object iOS extends Platform { override def toString: String = "ios" }
case object Newsstand extends Platform { override def toString: String = "newsstand" }
case object WindowsMobile extends Platform { override def toString: String = "windows-mobile" }

object Platform {
  def fromString(s: String): Option[Platform] = PartialFunction.condOpt(s) {
    case "android" => Android
    case "ios" => iOS
    case "windows-mobile" => WindowsMobile
    case "newsstand" => Newsstand
  }

  implicit val jf = new Format[Platform] {
    def reads(json: JsValue): JsResult[Platform] = json match {
      case JsString(s) => fromString(s) map { JsSuccess(_) } getOrElse JsError(s"$s is not a valid platform")
      case _ => JsError(s"Platform could not be decoded")
    }

    def writes(obj: Platform): JsValue = JsString(obj.toString)
  }
}
