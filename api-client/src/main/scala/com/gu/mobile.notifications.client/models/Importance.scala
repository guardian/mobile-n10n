package com.gu.mobile.notifications.client.models

import play.api.libs.json._

object Importance {
  sealed trait Importance
  case object Minor extends Importance
  case object Major extends Importance

  implicit val writes = new Writes[Importance] {
    override def writes(o: Importance): JsValue = o match {
      case Minor => JsString("Minor")
      case Major => JsString("Major")
    }
  }

  implicit val reads = new Reads[Importance] {
    override def reads(json: JsValue): JsResult[Importance] = json match {
      case JsString("Minor") => JsResult.applicativeJsResult.pure(Minor)
      case JsString("Major") => JsResult.applicativeJsResult.pure(Major)
      case _ => JsError(s"Unknown importance: $json")
    }
  }
}