package models

import play.api.libs.json._

case class DeviceToken(token: String)

object DeviceToken {
  implicit val deviceTokenJF: Format[DeviceToken] = new Format[DeviceToken] {
    override def reads(json: JsValue): JsResult[DeviceToken] = json match {
      case JsString(token) => JsSuccess(DeviceToken(token))
      case _ => JsError("Expected a string device token")
    }

    override def writes(o: DeviceToken): JsValue = JsString(o.token)
  }
}
