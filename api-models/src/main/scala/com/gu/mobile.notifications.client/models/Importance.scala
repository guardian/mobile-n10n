package com.gu.mobile.notifications.client.models

import play.api.libs.json.{JsValue, JsString, Writes}

object Importance {
  sealed trait Importance
  case object Minor extends Importance
  case object Major extends Importance

  implicit val jf = new Writes[Importance] {
    override def writes(o: Importance): JsValue = o match {
      case Minor => JsString("Minor")
      case Major => JsString("Major")
    }
  }
}