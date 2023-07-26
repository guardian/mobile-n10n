package models

import play.api.libs.json.Format

import PartialFunction.condOpt

sealed trait Importance
object Importance {
  case object Minor extends Importance
  case object Major extends Importance

  implicit val jf: Format[Importance] = JsonUtils.stringFormat(fromString)

  def fromString(s: String): Option[Importance] = condOpt(s) {
    case "Major" => Major
    case "Minor" => Minor
  }
}