package models

import play.api.libs.json._

sealed trait GoalType
object GoalType {
  case object Own extends GoalType
  case object Penalty extends GoalType
  case object Default extends GoalType

  implicit val jf: Format[GoalType] = new Format[GoalType] {
    override def writes(o: GoalType): JsValue = o match {
      case Own => JsString("Own")
      case Penalty => JsString("Penalty")
      case Default => JsString("Default")
    }

    override def reads(json: JsValue): JsResult[GoalType] = json match {
      case JsString("Own") => JsSuccess(Own)
      case JsString("Penalty") => JsSuccess(Penalty)
      case JsString("Default") => JsSuccess(Default)
      case _ => JsError("Unknown GoalType")
    }
  }
}
