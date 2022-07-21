package fakebreakingnews

import play.api.libs.json._

sealed trait Platform

case object Android extends Platform { override def toString: String = "android" }
case object Ios extends Platform { override def toString: String = "ios" }
case object Newsstand extends Platform { override def toString: String = "newsstand" }
case object AndroidEdition extends Platform { override def toString: String = "android-edition"}
case object IosEdition extends Platform { override def toString: String = "ios-edition"}
case object AndroidBeta extends Platform { override def toString: String = "android-beta" }
// only here to parse older notification reports
case object WindowsMobile extends Platform { override def toString: String = "windows-mobile" }

object Platform {
  def fromString(s: String): Option[Platform] = PartialFunction.condOpt(s.toLowerCase) {
    case "android" => Android
    case "ios" => Ios
    case "newsstand" => Newsstand
    case "windows-mobile" => WindowsMobile
    case "ios-edition" => IosEdition
    case "android-edition" => AndroidEdition
    case "android-beta" => AndroidBeta
  }

  implicit val jf = new Format[Platform] {
    def reads(json: JsValue): JsResult[Platform] = json match {
      case JsString(s) => fromString(s) map { JsSuccess(_) } getOrElse JsError(s"$s is not a valid platform")
      case _ => JsError(s"Platform could not be decoded")
    }

    def writes(obj: Platform): JsValue = JsString(obj.toString)
  }
}
