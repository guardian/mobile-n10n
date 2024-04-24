package com.gu.notifications.worker.delivery.fcm.models.payload

import play.api.libs.json.{Format, Json, JsError, JsValue, JsSuccess}

case class FcmResponse(name: String)

object FcmResponse {
  implicit val fcmResponseJf: Format[FcmResponse] = Json.format[FcmResponse]
}

case class FcmErrorPayload(code: Int, message: String, status: String) {
  override def toString() = s"Code [$code] Status [$status] - $message"
}

object FcmErrorPayload {
  implicit val fcmErrorPayloadJf: Format[FcmErrorPayload] = Json.format[FcmErrorPayload]
}

case class FcmError(error: FcmErrorPayload)

object FcmError {
  implicit val fcmErrorJf: Format[FcmError] = Json.format[FcmError]
}