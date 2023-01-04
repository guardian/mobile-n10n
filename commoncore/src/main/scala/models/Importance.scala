package models

import PartialFunction.condOpt

sealed trait Importance
object Importance {
  case object Minor extends Importance
  case object Major extends Importance

  implicit val jf = JsonUtils.stringFormat(fromString)

  def fromString(s: String): Option[Importance] = condOpt(s) {
    case "Major" => Major
    case "Minor" => Minor
  }
}