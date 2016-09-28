package azure

import models.JsonUtils

import scala.PartialFunction._

object TargetPlatform {

  import TargetPlatforms._

  implicit val jf = JsonUtils.stringFormat(fromString)

  def fromString(s: String): Option[TargetPlatform] = condOpt(s) {  // scalastyle:off cyclomatic.complexity
    case "windows" => Windows
    case "apple" => Apple
    case "gcm" => Gcm
    case "windowsphone" => Windowsphone
    case "adm" => Adm
    case "baidu" => Baidu
    case "template" => Template
    case "windowstemplate" => Windowstemplate
    case "appletemplate" => Appletemplate
    case "gcmtemplate" => Gcmtemplate
    case "windowsphonetemplate" => Windowsphonetemplate
    case "admtemplate" => Admtemplate
    case "baidutemplate" => Baidutemplate
  }

}

sealed trait TargetPlatform

object TargetPlatforms {
  case object Windows extends TargetPlatform { override def toString: String = "windows" }
  case object Apple extends TargetPlatform { override def toString: String = "apple" }
  case object Gcm extends TargetPlatform { override def toString: String = "gcm" }
  case object Windowsphone extends TargetPlatform { override def toString: String = "windowsphone" }
  case object Adm extends TargetPlatform { override def toString: String = "adm" }
  case object Baidu extends TargetPlatform { override def toString: String = "baidu" }
  case object Template extends TargetPlatform { override def toString: String = "template" }
  case object Windowstemplate extends TargetPlatform { override def toString: String = "windowstemplate" }
  case object Appletemplate extends TargetPlatform { override def toString: String = "appletemplate" }
  case object Gcmtemplate extends TargetPlatform { override def toString: String = "gcmtemplate" }
  case object Windowsphonetemplate extends TargetPlatform { override def toString: String = "windowsphonetemplate" }
  case object Admtemplate extends TargetPlatform { override def toString: String = "admtemplate" }
  case object Baidutemplate extends TargetPlatform { override def toString: String = "baidutemplate" }
}