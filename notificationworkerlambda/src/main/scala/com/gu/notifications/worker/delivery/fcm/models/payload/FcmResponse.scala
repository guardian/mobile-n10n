package com.gu.notifications.worker.delivery.fcm.models.payload

import play.api.libs.json.{JsPath, Json, JsError, JsValue, JsSuccess, Reads}
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

case class FcmResponse(name: String)

object FcmResponse {
  implicit val fcmResponseJf: Reads[FcmResponse] = Json.reads[FcmResponse]
}

case class FcmErrorPayload(code: Int, message: String, status: String, fcmErrorCode: Option[String]) {
  override def toString() = s"Code [$code] Status [$status] FcmCode[$fcmErrorCode] - $message"
}

object FcmErrorPayload {
  implicit val fcmErrorPayloadJf: Reads[FcmErrorPayload] = {
    ((JsPath \ "code").read[Int] and
      (JsPath \ "message").read[String] and
      (JsPath \ "status").read[String] and
      ((JsPath \ "details")(0) \ "errorCode").readNullable[String]
    )(FcmErrorPayload.apply _)   
  }
}

case class FcmError(error: FcmErrorPayload)

object FcmError {
  implicit val fcmErrorJf: Reads[FcmError] = Json.reads[FcmError]
}

